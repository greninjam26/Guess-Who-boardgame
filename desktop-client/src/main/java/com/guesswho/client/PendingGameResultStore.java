package com.guesswho.client;

import com.guesswho.game.GameResult;

import java.util.List;

/**
 * Holds completed games the server could not accept, so they can be uploaded on
 * a later connection.
 */
public interface PendingGameResultStore {
    /**
     * Adds one result to the store.
     *
     * @param gameResult completed game awaiting upload
     */
    void add(GameResult gameResult);

    /**
     * Reads every stored result.
     *
     * @return stored results in the order they were added
     */
    List<GameResult> readAll();

    /**
     * Replaces the stored results with those still awaiting upload.
     *
     * @param remaining results that could not be uploaded
     */
    void replaceAll(List<GameResult> remaining);
}
