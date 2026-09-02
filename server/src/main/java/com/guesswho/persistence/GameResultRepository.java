package com.guesswho.persistence;

import com.guesswho.game.GameResult;
import java.util.Map;

/**
 * Stores completed game snapshots independently of the transport that
 * submitted them.
 */
public interface GameResultRepository {
    /**
     * Persists a completed game result.
     *
     * @param gameResult completed-game snapshot to persist
     */
    void save(GameResult gameResult);

    /**
     * Stores a completed game, attributing the first participant to an account.
     *
     * <p>The account comes from whoever was signed in on the client that sent
     * the result, never from the request body: a body that could name an
     * account is a body that could claim somebody else's.</p>
     *
     * <p>The first participant is the one attributed because the client sending
     * a result is the machine its first player is sitting at. A second player
     * sharing the keyboard stays a name, as does everyone playing as a guest.</p>
     *
     * @param gameResult the completed game
     * @param accountId  the signed-in account, or null when a guest submitted it
     */
    void save(GameResult gameResult, Long accountId);

    /**
     * Stores a completed game in which more than one participant has an account.
     *
     * <p>An online game is the case the single-account method cannot express:
     * both players are signed in, on their own machines, and the result belongs
     * to both records rather than to whichever client happened to report it. The
     * server is the one holding that game, so it is the only place that knows
     * both accounts without being told them by a client — and a client that
     * could name the second account could put its losses on somebody else.</p>
     *
     * <p>Keyed by participant name rather than by play order. A positional list
     * is right only for as long as everybody remembers what the positions mean,
     * and one that slipped by a place would file a game on the wrong person's
     * record without anything failing.</p>
     *
     * @param gameResult                 the completed game
     * @param accountsByParticipantName  which account each named participant is,
     *                                   for those that have one; a participant
     *                                   the map does not mention, or maps to
     *                                   null, is stored unattributed
     */
    void saveOwnedBy(GameResult gameResult, Map<String, Long> accountsByParticipantName);
}
