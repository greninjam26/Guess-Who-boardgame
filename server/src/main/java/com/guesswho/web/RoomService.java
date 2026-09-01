package com.guesswho.web;

import com.guesswho.account.Account;
import com.guesswho.game.Game;
import com.guesswho.game.GameMode;
import com.guesswho.game.GameResult;
import com.guesswho.game.GameSnapshot;
import com.guesswho.game.PlayerGameStart;
import com.guesswho.game.QuestionMode;
import com.guesswho.persistence.GameResultRepository;
import com.guesswho.persistence.RoomRepository;
import com.guesswho.room.Room;
import com.guesswho.room.RoomCode;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Creating and joining online rooms.
 *
 * <p>Three deadlines, not one. A room nobody joins dies soonest: creating one
 * and walking away is the cheapest way to hold a code and a row for ever, and
 * it costs the person doing it a single request.</p>
 */
@Service
public class RoomService {
    private static final Logger LOG = LoggerFactory.getLogger(RoomService.class);

    /** Long enough to read a code down a phone, short enough to be cheap to abandon. */
    static final Duration UNJOINED_LIFETIME = Duration.ofMinutes(10);
    /** A game nobody has moved in. Long enough for somebody to make a cup of tea. */
    static final Duration IDLE_LIFETIME = Duration.ofMinutes(30);
    /** Nothing lives past this, however lively it looks. */
    static final Duration MAXIMUM_LIFETIME = Duration.ofHours(24);
    /**
     * How long a player has to make their move before losing the game.
     *
     * <p>Generous for thinking, short enough that somebody who walked away does
     * not cost their opponent half an hour of waiting. It is the abandoner who
     * pays: passing the turn instead would only move the stall along, and the
     * game would still need the sweep to end it. A forfeit gives the player who
     * stayed a result and a place on the leaderboard.</p>
     *
     * <p>Running out is necessary but not sufficient. The player who owes the
     * move has to have stopped being heard from as well, or a long think ends
     * the game of somebody sitting right there watching it — which is the exact
     * distinction presence is recorded to make.</p>
     */
    static final Duration TURN_LIMIT = Duration.ofMinutes(3);

    /** Enough for a game with each of a few friends; not enough to fill a table. */
    static final int MAX_OPEN_ROOMS = 5;
    /** Codes are unique, and a clash is chance rather than exhaustion. */
    private static final int CODE_ATTEMPTS = 5;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RoomRepository rooms;
    private final GameResultRepository results;

    /**
     * @param rooms   where rooms are kept
     * @param results where finished games are recorded
     */
    public RoomService(RoomRepository rooms, GameResultRepository results) {
        this.rooms = rooms;
        this.results = results;
    }

    /**
     * Opens a room and returns the code to share.
     *
     * @param host who is creating it
     * @return the new room
     * @throws TooManyRoomsException if this account already has enough open
     */
    public Room create(Account host) {
        if (rooms.openRoomCount(host.id()) >= MAX_OPEN_ROOMS) {
            //A rate limit bounds how fast rooms appear; only a cap bounds how
            //many exist at once, and it is the total that fills a database.
            throw new TooManyRoomsException(MAX_OPEN_ROOMS);
        }
        Instant now = Instant.now();
        Instant expiry = now.plus(UNJOINED_LIFETIME);
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            try {
                RoomRepository.StoredRoom created =
                        rooms.create(RoomCode.next(), host.id(), expiry);
                //Creating a room is being heard from. Without this the host is
                //absent until their first poll, and a guest who joins quickly
                //is told nobody is there.
                rooms.markSeenBy(created.code(), host.id(), now);
                return toRoom(created);
            }
            catch (RoomRepository.CodeTakenException clash) {
                //Two rooms cannot share a code. With 148 million of them this
                //is chance, so trying again is the whole remedy.
                continue;
            }
        }
        throw new IllegalStateException("Could not find an unused room code");
    }

    /**
     * Joins a waiting room.
     *
     * @param code  the code the guest typed
     * @param guest who is joining
     * @return the room they joined
     * @throws NoSuchRoomException  if the code opens nothing
     * @throws RoomNotJoinableException if it is full, finished, or their own
     */
    public Room join(String code, Account guest) {
        String tidied = RoomCode.normalise(code);
        if (tidied == null) {
            //Answered without a query: a code of the wrong shape is a typo, and
            //there is nothing to look up.
            throw new NoSuchRoomException();
        }
        Instant now = Instant.now();
        RoomRepository.StoredRoom room = rooms.findByCode(tidied)
                .filter(stored -> isLive(stored, now))
                .orElseThrow(NoSuchRoomException::new);
        if (room.hostAccountId() == guest.id()) {
            throw new RoomNotJoinableException("You cannot join your own room");
        }
        if (room.status() != RoomStatus.WAITING) {
            throw new RoomNotJoinableException("That game has already started");
        }

        Game game = startGame(room.hostName(), guest.username());
        boolean joined = rooms.join(tidied, guest.id(), serialise(game.snapshot()),
                now.plus(IDLE_LIFETIME));
        if (!joined) {
            //Somebody else got there between the read and the write. The
            //conditional update is what decides, so this is a real answer
            //rather than a race that both callers won.
            throw new RoomNotJoinableException("Somebody else joined that game first");
        }
        //Joining is being heard from, for the same reason creating is.
        rooms.markSeenBy(tidied, guest.id(), now);
        return rooms.findByCode(tidied).map(RoomService::toRoom)
                .orElseThrow(NoSuchRoomException::new);
    }

    /**
     * Finds a room, for somebody who is in it.
     *
     * @param code      the room's code
     * @param accountId who is asking
     * @return the room, or empty when it does not exist or is not theirs
     */
    public Optional<RoomRepository.StoredRoom> forPlayer(String code, long accountId) {
        String tidied = RoomCode.normalise(code);
        if (tidied == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        //Somebody who is not in the room is told the same thing as somebody
        //whose code was wrong. Distinguishing them turns the endpoint into a
        //way to find out which codes are live.
        return rooms.findByCode(tidied)
                .filter(room -> room.includes(accountId))
                .filter(room -> isLive(room, now));
    }

    /**
     * Whether a room is still within its lifetime.
     *
     * <p>The sweep runs every five minutes, so between a room expiring and the
     * sweep reaching it there is a window in which the row is still there. It
     * must not be playable in that window: a game that has expired is over, and
     * answering out of it would let a poll forfeit and re-date a room the
     * server had already given up on.</p>
     */
    private static boolean isLive(RoomRepository.StoredRoom room, Instant now) {
        return room.expiresAt().isAfter(now);
    }

    /**
     * Notes that a player has just been heard from.
     *
     * <p>Called for every request one of the two players makes about a room,
     * before anything that could refuse it. Making a move is the strongest
     * evidence somebody is at their machine, and it has to count even when the
     * rules turn the move down: a player pressing a button they are not allowed
     * to press yet is unmistakably still there, and recording presence only for
     * moves that succeed would let them be forfeited while actively trying to
     * play.</p>
     *
     * <p>Outside the move's transaction rather than inside it, and deliberately
     * so. A rejected move rolls its transaction back, which would take the
     * player's presence with it — and a nested transaction of its own deadlocks
     * against the very row the move is about to write.</p>
     *
     * @param code      the room's code
     * @param accountId who has been heard from
     */
    public void markPresent(String code, long accountId) {
        String tidied = RoomCode.normalise(code);
        if (tidied == null) {
            return;
        }
        //An account that is in no such room changes nothing: the statement is
        //scoped to the two players, so this needs no check of its own.
        rooms.markSeenBy(tidied, accountId, Instant.now());
    }

    /**
     * Chooses the character a player will be guessed at.
     *
     * @param code      the room's code
     * @param account   who is choosing
     * @param character the character they are holding
     * @return the room as they may now see it
     * @throws NoSuchRoomException if the code opens nothing of theirs
     * @throws IllegalStateException if they have already chosen
     */
    @Transactional
    public RoomState chooseCharacter(
            String code, Account account, String character, String moveKey) {
        //Through the same path as every other move, so it carries a key too.
        //Choosing is a move like any other: the response can be lost on the way
        //back, and a retry without a key is refused as choosing twice — which
        //tells a player their choice failed when it did not.
        //
        //Named by whoever is asking, so a player can only ever choose their
        //own: the username comes from the token, not from the request.
        return move(code, account, moveKey,
                game -> game.selectCharacter(account.username(), character));
    }

    /**
     * Asks the opponent a question.
     *
     * @param code     the room's code
     * @param account  who is asking
     * @param question what they asked
     * @return their view of the game afterwards
     */
    @Transactional
    public RoomState ask(String code, Account account, String question, String moveKey) {
        return move(code, account, moveKey,
                game -> game.askQuestion(account.username(), question));
    }

    /**
     * Answers the question the opponent asked.
     *
     * @param code    the room's code
     * @param account who is answering
     * @param answer  what they said
     * @return their view of the game afterwards
     */
    @Transactional
    public RoomState answer(String code, Account account, boolean answer, String moveKey) {
        return move(code, account, moveKey,
                game -> game.answerQuestion(account.username(), answer));
    }

    /**
     * Guesses the opponent's character, which ends the game either way.
     *
     * @param code      the room's code
     * @param account   who is guessing
     * @param character who they think their opponent is holding
     * @return their view of the game afterwards
     */
    @Transactional
    public RoomState guess(String code, Account account, String character, String moveKey) {
        return move(code, account, moveKey,
                game -> game.guessOpponent(account.username(), character));
    }

    /**
     * Applies one move and stores what it left behind.
     *
     * <p>Every move goes through here, so every move is validated by the rules
     * rather than by the endpoint that called it. A client cannot ask out of
     * turn or answer its own question, because the game refuses rather than
     * because the controller remembered to check.</p>
     */
    //Not annotated: Spring cannot intercept a private method, and a call from
    //inside the same object does not pass through the proxy either. The
    //transaction has to begin at the public method a client actually reaches,
    //which is where the annotations are.
    private RoomState move(String code, Account account, String moveKey,
            java.util.function.Consumer<Game> play) {
        RoomRepository.StoredRoom room = forPlayer(code, account.id())
                .orElseThrow(NoSuchRoomException::new);
        if (room.gameState() == null) {
            throw new RoomNotJoinableException("Nobody has joined that game yet");
        }
        //Claimed before the move is applied. A key that was already used means
        //this is a retry of something that has happened, so the answer is the
        //state it left behind rather than the move happening again.
        if (moveKey != null && !rooms.claimMove(room.code(), moveKey)) {
            return state(room.code(), account.id());
        }
        Game game = restore(room.gameState());
        play.accept(game);
        RoomStatus status = game.getStatus() == com.guesswho.game.GameStatus.FINISHED
                ? RoomStatus.FINISHED
                : room.status();
        boolean landed = rooms.updateGame(room.code(), serialise(game.snapshot()), status,
                nextDeadline(room), room.version());
        if (!landed) {
            //Somebody else moved between reading this state and writing the
            //result. Applying it anyway would replace their move with one
            //worked out from a game that no longer exists.
            throw new RoomMovedOnException();
        }
        if (status == RoomStatus.FINISHED) {
            recordResult(game, room);
        }
        return state(room.code(), account.id());
    }

    /**
     * Reads a room as one player may see it.
     *
     * @param code      the room's code
     * @param accountId who is asking
     * @return their view of it
     * @throws NoSuchRoomException if the code opens nothing of theirs
     */
    public RoomState state(String code, long accountId) {
        Instant now = Instant.now();
        RoomRepository.StoredRoom room = forPlayer(code, accountId)
                .orElseThrow(NoSuchRoomException::new);
        //Reading counts as being present. A player waiting on their opponent
        //makes no moves at all, and treating only moves as presence would have
        //them vanish while they sat watching the board.
        rooms.markSeenBy(room.code(), accountId, now);
        //Checked here rather than only on a schedule: the player who is waiting
        //is the one polling, so this is where they find out, within a couple of
        //seconds rather than whenever a sweep next runs.
        RoomRepository.StoredRoom current = forfeitIfAbandoned(room, accountId, now)
                .orElse(room);
        //Projected from the row already read, so this player's own poll does
        //not make them look freshly present to themselves.
        return RoomProjection.forPlayer(current, accountId, now);
    }

    /**
     * Ends a game whose turn ran out, in favour of whoever is still there.
     *
     * <p>A turn running out is not on its own a reason to take somebody's game
     * away. The player who owes the move has to have gone as well, or a slow
     * decision becomes a loss — and the whole point of recording presence is to
     * tell somebody thinking from somebody whose laptop is shut.</p>
     *
     * @param room    the room as it stands
     * @param askedBy the account whose request this is
     * @param now     the moment to judge against
     * @return the room after forfeiting, or empty when nothing was forfeited
     */
    private Optional<RoomRepository.StoredRoom> forfeitIfAbandoned(
            RoomRepository.StoredRoom room, long askedBy, Instant now) {
        if (room.status() != RoomStatus.IN_PROGRESS || room.gameState() == null) {
            return Optional.empty();
        }
        if (!isLive(room, now)) {
            //Already past its expiry and waiting for the sweep. Forfeiting here
            //would settle a game the server has given up on, and the write
            //would carry a fresh deadline that brings the room back to life.
            return Optional.empty();
        }
        if (room.updatedAt().isAfter(now.minus(TURN_LIMIT))) {
            return Optional.empty();
        }
        Game game = restore(room.gameState());
        String owes = game.getPlayerOwingAMove().orElse(null);
        if (owes == null) {
            return Optional.empty();
        }
        if (!game.hasSelectedCharacter(room.hostName())
                || !game.hasSelectedCharacter(room.guestName())) {
            //There is no game to win until both players are holding somebody.
            //A room abandoned during choosing has nothing to record — a result
            //needs both characters, and the room's own expiry already covers
            //the case of two people who never got started.
            return Optional.empty();
        }
        boolean owedByHost = owes.equals(room.hostName());
        if (isStillThere(room, owedByHost, askedBy, now)) {
            //Watching the board and taking their time. The room's own expiry is
            //what bounds a game two people leave running.
            return Optional.empty();
        }
        String stayed = owedByHost ? room.guestName() : room.hostName();
        game.finish(stayed);
        //Version-checked like any other write: two players polling at the same
        //moment would otherwise both forfeit the same game.
        //
        //Carrying the room's existing expiry rather than a fresh one: a game
        //that has just been forfeited needs no more time than it already had.
        boolean landed = rooms.updateGame(room.code(), serialise(game.snapshot()),
                RoomStatus.FINISHED, room.expiresAt(), room.version());
        if (!landed) {
            return Optional.empty();
        }
        recordResult(game, room);
        return rooms.findByCode(room.code());
    }

    /**
     * Writes a finished online game to the record both players share.
     *
     * <p>Called only where the version-checked write landed, which is what makes
     * it happen exactly once. Both players poll a finished game and either could
     * be the one to notice, so anything gated on "the game is over" rather than
     * "this call is what ended it" would record the same game twice.</p>
     *
     * <p>A failure here loses the record, not the game. The players have their
     * result either way, and refusing to end a game because the leaderboard was
     * briefly unwritable would be the worse trade.</p>
     */
    private void recordResult(Game game, RoomRepository.StoredRoom room) {
        try {
            GameResult played = game.getGameResult();
            //Rebuilt with the mode the server knows and the game does not. A
            //Game cannot tell an online opponent from somebody sharing the
            //keyboard — both are two humans — so left alone it files every
            //online game as hotseat, on a board that is meant to be the
            //competitive one.
            GameResult online = new GameResult(played.participants(), played.winner(),
                    GameMode.PVP_ONLINE, played.difficulty(), played.questionMode());
            //Play order is host then guest, because that is the order the game
            //was started in when the room was joined.
            //asList rather than List.of: the guest's account is a Long, and
            //List.of throws on a null rather than storing an unattributed row.
            results.saveOwnedBy(online,
                    java.util.Arrays.asList(room.hostAccountId(), room.guestAccountId()));
        }
        catch (RuntimeException unrecorded) {
            LOG.warn("Could not record the result of online game {}", room.code(),
                    unrecorded);
        }
    }

    /**
     * Whether the player who owes the move is still around to make it.
     *
     * @param room        the room as it stands
     * @param owedByHost  whether it is the host who owes the move
     * @param askedBy     the account whose request this is
     * @param now         the moment to judge against
     * @return true when they should be given longer
     */
    private static boolean isStillThere(
            RoomRepository.StoredRoom room, boolean owedByHost, long askedBy, Instant now) {
        //Boxed on both arms deliberately: a long on one and a Long on the other
        //makes the ternary unbox, and a room with no guest would throw here
        //rather than answering.
        Long theirs = owedByHost ? Long.valueOf(room.hostAccountId()) : room.guestAccountId();
        if (theirs != null && theirs == askedBy) {
            //Whoever is asking is here by definition — their request is
            //arriving as this runs. The stored timestamp is deliberately the
            //one from before this request, so without this a player who came
            //back after a break would forfeit on their own first poll.
            return true;
        }
        return Presence.isPresent(
                owedByHost ? room.hostLastSeen() : room.guestLastSeen(), now);
    }

    /**
     * The deadline a room should carry after activity, never past its ceiling.
     *
     * <p>Pushing the deadline out on every move means a game two people keep
     * poking at never expires at all. The ceiling is measured from when the
     * room was opened, so it cannot be extended by playing.</p>
     */
    private static Instant nextDeadline(RoomRepository.StoredRoom room) {
        Instant idle = Instant.now().plus(IDLE_LIFETIME);
        Instant ceiling = room.createdAt().plus(MAXIMUM_LIFETIME);
        return idle.isBefore(ceiling) ? idle : ceiling;
    }

    private static Game restore(String gameState) {
        try {
            return Game.restoredFrom(deserialise(gameState));
        }
        catch (Exception unrestorable) {
            throw new IllegalStateException("The stored game could not be read",
                    unrestorable);
        }
    }

    /**
     * Removes rooms whose time is up.
     *
     * @return how many were removed
     */
    public int sweepExpired() {
        return rooms.deleteExpired(Instant.now());
    }

    private static Game startGame(String hostName, String guestName) {
        try {
            Game game = new Game();
            //The host is first, which is the same rule local play uses; who
            //moves first is decided here rather than by either client.
            game.startPlayerGame(hostName, 0, guestName, 0,
                    PlayerGameStart.RANDOM, QuestionMode.PRESET);
            return game;
        }
        catch (Exception unloadable) {
            throw new IllegalStateException("The board could not be loaded", unloadable);
        }
    }

    static String serialise(GameSnapshot snapshot) {
        return JSON_MAPPER.writeValueAsString(snapshot);
    }

    static GameSnapshot deserialise(String json) {
        return JSON_MAPPER.readValue(json, GameSnapshot.class);
    }

    private static Room toRoom(RoomRepository.StoredRoom stored) {
        return new Room(stored.code(), stored.status(), stored.hostName(),
                stored.guestName(), stored.expiresAt());
    }

    /**
     * Thrown when the room changed between reading it and writing the result.
     *
     * <p>Not an error in the move: it was worked out from a game that has since
     * moved on. The client polls, sees the new state, and the player acts from
     * there.</p>
     */
    public static class RoomMovedOnException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /** Creates the exception. */
        public RoomMovedOnException() {
            super("Your opponent moved first. The game has moved on.");
        }
    }

    /** Thrown when a code opens nothing the caller may see. */
    public static class NoSuchRoomException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /** Creates the exception. */
        public NoSuchRoomException() {
            super("No such room");
        }
    }

    /** Thrown when a room exists but cannot be joined. */
    public static class RoomNotJoinableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * @param message why not
         */
        public RoomNotJoinableException(String message) {
            super(message);
        }
    }

    /** Thrown when an account already has as many rooms open as it may. */
    public static class TooManyRoomsException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * @param maximum how many are allowed
         */
        public TooManyRoomsException(int maximum) {
            super("You already have " + maximum + " games open. Finish one first.");
        }
    }
}
