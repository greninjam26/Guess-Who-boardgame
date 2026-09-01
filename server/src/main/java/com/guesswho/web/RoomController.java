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
     * <p>Optional, because a request without one cannot be recognised as a
     * retry anyway and refusing it would only turn a missing header into a lost
     * move. Clients should always send one.</p>
     */
    static final String MOVE_KEY_HEADER = "Idempotency-Key";

    private final RoomService rooms;
    private final SessionService sessions;

    /**
     * @param rooms    creates and joins rooms
     * @param sessions works out who is asking
     */
    public RoomController(RoomService rooms, SessionService sessions) {
        this.rooms = rooms;
        this.sessions = sessions;
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
        Account player = requireSignedIn(authorization);
        if (choice == null || choice.character() == null || choice.character().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose a character");
        }
        try {
            return rooms.chooseCharacter(code, player, choice.character(), moveKey);
        }
        catch (RoomService.NoSuchRoomException unknown) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No game with that code");
        }
        catch (RoomService.RoomNotJoinableException notReady) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, notReady.getMessage());
        }
        catch (IllegalStateException | IllegalArgumentException refused) {
            //Choosing twice, or naming somebody who is not on the board.
            throw new ResponseStatusException(HttpStatus.CONFLICT, refused.getMessage());
        }
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
        Account player = requireSignedIn(authorization);
        if (asked == null || asked.question() == null || asked.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask a question");
        }
        return played(() -> rooms.ask(code, player, asked.question(), moveKey));
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
        Account player = requireSignedIn(authorization);
        if (given == null || given.answer() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer yes or no");
        }
        return played(() -> rooms.answer(code, player, given.answer(), moveKey));
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
        Account player = requireSignedIn(authorization);
        if (guess == null || guess.character() == null || guess.character().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name a character");
        }
        return played(() -> rooms.guess(code, player, guess.character(), moveKey));
    }

    /**
     * Runs a move and turns the rules' refusals into answers a client can act on.
     *
     * <p>409 for every refusal the rules make: asking out of turn, answering
     * your own question, guessing before the opponent has chosen. Each is a
     * request that was well formed and arrived at the wrong moment, which is
     * what a conflict is.</p>
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

    private Account requireSignedIn(String authorization) {
        return sessions.accountFor(BearerToken.from(authorization))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Sign in to play online"));
    }
}
