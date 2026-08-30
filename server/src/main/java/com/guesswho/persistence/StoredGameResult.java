package com.guesswho.persistence;

import com.guesswho.game.GameResult;

import java.time.LocalDateTime;

/**
 * A completed game together with its database identity and creation time.
 *
 * @param id database identity
 * @param createdAt time the result was stored
 * @param gameResult completed-game snapshot
 */
public record StoredGameResult(long id, LocalDateTime createdAt, GameResult gameResult) {
}
