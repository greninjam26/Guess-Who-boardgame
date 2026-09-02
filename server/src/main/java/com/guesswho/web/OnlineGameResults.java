package com.guesswho.web;

import com.guesswho.game.Game;
import com.guesswho.game.GameMode;
import com.guesswho.game.GameResult;
import com.guesswho.persistence.GameResultRepository;
import com.guesswho.persistence.RoomRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Turns a finished online room into the record both players share.
 *
 * <p>Its own class because it is its own decision. Two things here are true of
 * online games and of nothing else: the mode is one a {@link Game} cannot work
 * out for itself, and both participants have accounts. Keeping them beside the
 * room lifecycle made {@code RoomService} answer two questions at once.</p>
 *
 * <p>Nothing here is caught. Writing the result is part of finishing the game,
 * not a courtesy afterwards — see {@link #record} for why that is the safer
 * way round.</p>
 */
@Component
class OnlineGameResults {
    private final GameResultRepository results;

    /**
     * @param results where finished games are stored
     */
    OnlineGameResults(GameResultRepository results) {
        this.results = results;
    }

    /**
     * Records a finished online game against both players' accounts.
     *
     * <p>Deliberately allowed to fail the caller's transaction. Rooms and
     * results live in the same database, so a failed insert here means that
     * database is unavailable — in which case the write that finished the room
     * has not committed either, and letting both fail together leaves the game
     * exactly as it was. The player retries, or their client polls again, and
     * it happens then.</p>
     *
     * <p>Swallowing it would be worse in both directions. On a move the
     * exception has already marked the transaction rollback-only, so the room
     * update is undone regardless and catching only hides why; on a poll the
     * room finishes and the result is lost with nothing left to retry from.</p>
     *
     * @param game the finished game
     * @param room the room it was played in
     */
    void record(Game game, RoomRepository.StoredRoom room) {
        GameResult played = game.getGameResult();
        //Rebuilt with the mode the server knows and the game does not. A Game
        //cannot tell an online opponent from somebody sharing the keyboard —
        //both are two humans — so left alone it files every online game as
        //hotseat, on a board that is meant to be the competitive one.
        GameResult online = new GameResult(played.participants(), played.winner(),
                GameMode.PVP_ONLINE, played.difficulty(), played.questionMode());
        results.saveOwnedBy(online, accountsFor(room));
    }

    /**
     * Which account each player's name belongs to.
     *
     * <p>By name rather than by position. Both are correct today, because play
     * order is host then guest, but only one of them stays correct if that ever
     * changes — and a positional list that silently slipped by one would put a
     * game on the wrong person's record.</p>
     */
    private static Map<String, Long> accountsFor(RoomRepository.StoredRoom room) {
        //A plain HashMap: Map.of refuses a null value, and a room whose guest
        //has been removed still has a game worth recording.
        Map<String, Long> accounts = new HashMap<>();
        accounts.put(room.hostName(), room.hostAccountId());
        if (room.guestName() != null) {
            accounts.put(room.guestName(), room.guestAccountId());
        }
        return accounts;
    }
}
