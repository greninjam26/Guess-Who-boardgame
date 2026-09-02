package com.guesswho.web;

import com.guesswho.account.Account;
import com.guesswho.room.Room;
import com.guesswho.room.RoomState;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Opening and joining online rooms.
 *
 * <p>The first endpoints that require signing in. Local play stays open to
 * guests, but an online game has to know who is on each side: to attribute the
 * result, to let somebody reconnect to their own game, and to stop a stranger
 * acting as either player.</p>
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    /**
     * The header a client puts its own key for a move in.
     *
     * <p>Required. A move without one cannot be recognised as a retry, so a
     * lost response becomes a move applied twice — and since this project ships
     * the only client, a request arriving without a key is a bug rather than
     * somebody else's client being awkward. Better to say so than to accept it
     * and lose the guarantee quietly.</p>
     */
    static final String MOVE_KEY_HEADER = "Idempotency-Key";

    /** Matches the column in V9__add_move_keys.sql. */
    private static final int MAX_MOVE_KEY = 64;

    private final RoomService rooms;
    private final SessionService sessions;
    private final RateLimiter limiter;

    /**
     * @param rooms    creates and joins rooms
     * @param sessions works out who is asking
     * @param limiter  bounds how fast one account can open rooms and move
     */
    public RoomController(RoomService rooms, SessionService sessions, RateLimiter limiter) {
        this.rooms = rooms;
        this.sessions = sessions;
        this.limiter = limiter;
    }

    /**
     * Opens a room and returns the code to share.
     *
     * @param authorization the bearer token of whoever is creating it
     * @return the new room
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Room create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        Account host = requireSignedIn(authorization);
        //The five-open-rooms cap bounds how many exist; this bounds the churn of
        //opening and abandoning them, which the cap cannot see because an
        //abandoned room stops counting the moment it expires.
        Callers.require(limiter, "open-room", host.id(), RateLimits.OPEN_ROOM);
        try {
            return rooms.create(host);
        }
        catch (RoomService.TooManyRoomsException tooMany) {
            //429 rather than 400: the request was fine, there are just too many
            //already, and trying later is the remedy.
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, tooMany.getMessage());
        }
    }

    /**
     * Joins a waiting room.
     *
     * @param code          the code the guest typed
     * @param authorization the bearer token of whoever is joining
     * @return the room they joined
     */
    @PostMapping("/{code}/players")
    @ResponseStatus(HttpStatus.CREATED)
    public Room join(
            @PathVariable String code,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        Account guest = requireSignedIn(authorization);
        try {
            return rooms.join(code, guest);
        }
        catch (RoomService.NoSuchRoomException unknown) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No game with that code");
        }
        catch (RoomService.RoomNotJoinableException closed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, closed.getMessage());
        }
    }

    /**
     * Reads the game as the caller may see it.
     *
     * <p>What comes back depends on who is asking. The opponent's character is
     * not in it, and not because the client is trusted to hide it.</p>
     *
     * @param code          the room's code
     * @param authorization the bearer token of whoever is asking
     * @return their view of the game
     */
    @GetMapping("/{code}/state")
    public RoomState state(
            @PathVariable String code,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        Account player = requireSignedIn(authorization);
        try {
            return rooms.state(code, player.id());
        }
        catch (RoomService.NoSuchRoomException unknown) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No game with that code");
        }
    }

    /**
     * Reveals a finished game to one of the two people who played it.
     *
     * <p>Its own endpoint, returning its own type. The state endpoint is built
     * so that nothing on it can carry the opponent's character; this is where
     * that becomes sayable, and only once the game is over.</p>
     *
     * @param code          the room's code
     * @param authorization the bearer token of whoever is asking
     * @return both characters, both promises, and how the answers held up
     */
    @GetMapping("/{code}/reveal")
    public com.guesswho.room.GameReveal reveal(
            @PathVariable String code,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        Account player = requireSignedIn(authorization);
        try {
            return rooms.reveal(code, player.id());
        }
        catch (RoomService.NoSuchRoomException unknown) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No game with that code");
        }
        catch (RoomService.GameNotOverException notOver) {
            //409, like every other well-formed request that arrived at the wrong
            //moment.
            throw new ResponseStatusException(HttpStatus.CONFLICT, notOver.getMessage());
        }
    }

    /**
     * Chooses the character the caller will be guessed at.
     *
     * @param code          the room's code
     * @param choice        the character they are holding
     * @param authorization the bearer token of whoever is choosing
     * @return their view of the game afterwards
     */
    @PostMapping("/{code}/character")
    public RoomState chooseCharacter(
            @PathVariable String code,
            @RequestBody CharacterChoice choice,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            @RequestHeader(value = MOVE_KEY_HEADER, required = false) String moveKey) {
        Mover mover = mover(code, authorization, moveKey);
        if (choice == null || choice.character() == null || choice.character().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose a character");
        }
        //Through the same handler as every other move, not a catch chain of its
        //own. Choosing is a move, it goes through RoomService.move like one, and
        //it can therefore lose the same race any other move can — a duplicated
        //chain that forgot RoomMovedOnException turned that race into a 500.
        return played(() -> rooms.chooseCharacter(
                code, mover.player(), choice.character(), mover.key()));
    }

    /**
     * Asks the opponent a question.
     *
     * @param code          the room's code
     * @param asked         the question
     * @param authorization the bearer token of whoever is asking
     * @return their view of the game afterwards
     */
    @PostMapping("/{code}/questions")
    public RoomState ask(
            @PathVariable String code,
            @RequestBody QuestionAsked asked,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            @RequestHeader(value = MOVE_KEY_HEADER, required = false) String moveKey) {
        Mover mover = mover(code, authorization, moveKey);
        if (asked == null || asked.question() == null || asked.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask a question");
        }
        return played(() -> rooms.ask(code, mover.player(), asked.question(), mover.key()));
    }

    /**
     * Answers the question the opponent asked.
     *
     * @param code          the room's code
     * @param given         the answer
     * @param authorization the bearer token of whoever is answering
     * @return their view of the game afterwards
     */
    @PostMapping("/{code}/answers")
    public RoomState answer(
            @PathVariable String code,
            @RequestBody AnswerGiven given,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            @RequestHeader(value = MOVE_KEY_HEADER, required = false) String moveKey) {
        Mover mover = mover(code, authorization, moveKey);
        if (given == null || given.answer() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer yes or no");
        }
        return played(() -> rooms.answer(code, mover.player(), given.answer(), mover.key()));
    }

    /**
     * Guesses the opponent's character, which ends the game either way.
     *
     * @param code          the room's code
     * @param guess         who they think it is
     * @param authorization the bearer token of whoever is guessing
     * @return their view of the game afterwards
     */
    @PostMapping("/{code}/guesses")
    public RoomState guess(
            @PathVariable String code,
            @RequestBody CharacterChoice guess,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            @RequestHeader(value = MOVE_KEY_HEADER, required = false) String moveKey) {
        Mover mover = mover(code, authorization, moveKey);
        if (guess == null || guess.character() == null || guess.character().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name a character");
        }
        return played(() -> rooms.guess(code, mover.player(), guess.character(), mover.key()));
    }

    /**
     * Who is making a move, and under what key.
     *
     * @param player whoever the bearer token belongs to
     * @param key    their own key for this move
     */
    private record Mover(Account player, String key) {
    }

    /**
     * The three things every move endpoint does before it does its own work.
     *
     * <p>Presence is recorded in the middle of them, before the key check and
     * before the rules get a say, because a request that is about to be refused
     * still proves its sender is sitting there — and a player retrying a move
     * they are not allowed to make yet must not look to the forfeit rule like
     * somebody who has walked away. Four copies of that ordering were four
     * chances to get it subtly wrong.</p>
     */
    private Mover mover(String code, String authorization, String moveKey) {
        Account player = requireSignedIn(authorization);
        rooms.markPresent(code, player.id());
        //After presence, so a player being throttled still counts as there. A
        //limit that made somebody look absent would forfeit their game as a side
        //effect of protecting the server.
        Callers.require(limiter, "move", player.id(), RateLimits.MOVE);
        return new Mover(player, requireMoveKey(moveKey));
    }

    /**
     * Runs a move and turns the rules' refusals into answers a client can act on.
     *
     * <p>409 for every refusal the rules make: asking out of turn, answering
     * your own question, choosing a character twice or naming one that is not
     * on the board, guessing before the opponent has chosen. Each is a request
     * that was well formed and arrived at the wrong moment, which is what a
     * conflict is.</p>
     */
    private RoomState played(java.util.function.Supplier<RoomState> move) {
        try {
            return move.get();
        }
        catch (RoomService.NoSuchRoomException unknown) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No game with that code");
        }
        catch (RoomService.RoomNotJoinableException notReady) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, notReady.getMessage());
        }
        catch (RoomService.RoomMovedOnException movedOn) {
            //Also a conflict, and for the same reason as the others: the
            //request was fine and arrived at the wrong moment.
            throw new ResponseStatusException(HttpStatus.CONFLICT, movedOn.getMessage());
        }
        catch (IllegalStateException | IllegalArgumentException refused) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, refused.getMessage());
        }
    }

    /**
     * A question one player asked.
     *
     * @param question the question text
     */
    public record QuestionAsked(String question) {
    }

    /**
     * An answer to the question that was asked.
     *
     * @param answer yes or no
     */
    public record AnswerGiven(Boolean answer) {
    }

    /**
     * The character a player is holding.
     *
     * @param character the character's name
     */
    public record CharacterChoice(String character) {
    }

    /**
     * Checks a move carries a usable key.
     *
     * <p>The length matters as much as the presence: an oversized key reaches
     * the database, breaks the column, and comes back as a 500 for what is
     * plainly a bad request.</p>
     */
    private String requireMoveKey(String moveKey) {
        if (moveKey == null || moveKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Every move needs an " + MOVE_KEY_HEADER + " header");
        }
        String key = moveKey.trim();
        if (key.length() > MAX_MOVE_KEY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    MOVE_KEY_HEADER + " must be at most " + MAX_MOVE_KEY + " characters");
        }
        return key;
    }

    private Account requireSignedIn(String authorization) {
        return sessions.accountFor(BearerToken.from(authorization))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Sign in to play online"));
    }
}
