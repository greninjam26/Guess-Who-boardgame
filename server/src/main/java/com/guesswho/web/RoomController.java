package com.guesswho.web;

import com.guesswho.account.Account;
import com.guesswho.room.Room;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private Account requireSignedIn(String authorization) {
        return sessions.accountFor(BearerToken.from(authorization))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Sign in to play online"));
    }
}
