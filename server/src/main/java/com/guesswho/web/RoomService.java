package com.guesswho.web;

import com.guesswho.account.Account;
import com.guesswho.game.Game;
import com.guesswho.game.GameSnapshot;
import com.guesswho.game.PlayerGameStart;
import com.guesswho.game.QuestionMode;
import com.guesswho.persistence.RoomRepository;
import com.guesswho.room.Room;
import com.guesswho.room.RoomCode;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
    /** Long enough to read a code down a phone, short enough to be cheap to abandon. */
    static final Duration UNJOINED_LIFETIME = Duration.ofMinutes(10);
    /** A game nobody has moved in. Long enough for somebody to make a cup of tea. */
    static final Duration IDLE_LIFETIME = Duration.ofMinutes(30);
    /** Nothing lives past this, however lively it looks. */
    static final Duration MAXIMUM_LIFETIME = Duration.ofHours(24);
    /** Enough for a game with each of a few friends; not enough to fill a table. */
    static final int MAX_OPEN_ROOMS = 5;
    /** Codes are unique, and a clash is chance rather than exhaustion. */
    private static final int CODE_ATTEMPTS = 5;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RoomRepository rooms;

    /**
     * @param rooms where rooms are kept
     */
    public RoomService(RoomRepository rooms) {
        this.rooms = rooms;
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
        Instant expiry = Instant.now().plus(UNJOINED_LIFETIME);
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            try {
                return toRoom(rooms.create(RoomCode.next(), host.id(), expiry));
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
        RoomRepository.StoredRoom room = rooms.findByCode(tidied)
                .orElseThrow(NoSuchRoomException::new);
        if (room.hostAccountId() == guest.id()) {
            throw new RoomNotJoinableException("You cannot join your own room");
        }
        if (room.status() != RoomStatus.WAITING) {
            throw new RoomNotJoinableException("That game has already started");
        }

        Game game = startGame(room.hostName(), guest.username());
        boolean joined = rooms.join(tidied, guest.id(), serialise(game.snapshot()),
                Instant.now().plus(IDLE_LIFETIME));
        if (!joined) {
            //Somebody else got there between the read and the write. The
            //conditional update is what decides, so this is a real answer
            //rather than a race that both callers won.
            throw new RoomNotJoinableException("Somebody else joined that game first");
        }
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
        //Somebody who is not in the room is told the same thing as somebody
        //whose code was wrong. Distinguishing them turns the endpoint into a
        //way to find out which codes are live.
        return rooms.findByCode(tidied).filter(room -> room.includes(accountId));
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
    public RoomState chooseCharacter(
            String code, Account account, String character) {
        RoomRepository.StoredRoom room = forPlayer(code, account.id())
                .orElseThrow(NoSuchRoomException::new);
        if (room.gameState() == null) {
            throw new RoomNotJoinableException("Nobody has joined that game yet");
        }
        Game game = restore(room.gameState());
        //Named by whoever is asking, so a player can only ever choose their
        //own: the username comes from the token, not from the request.
        game.selectCharacter(account.username(), character);
        rooms.updateGame(room.code(), serialise(game.snapshot()), room.status(),
                nextDeadline(room));
        return state(room.code(), account.id());
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
        rooms.updateGame(room.code(), serialise(game.snapshot()), status,
                nextDeadline(room));
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
        return forPlayer(code, accountId)
                .map(room -> RoomProjection.forPlayer(room, accountId))
                .orElseThrow(NoSuchRoomException::new);
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
