package com.guesswho.ui;

import com.guesswho.game.GameSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * A game in progress, as it is written to disk.
 *
 * <p>The game itself is a {@link GameSnapshot}; what is here besides is the
 * part of a half-finished game that lives in the interface rather than in the
 * rules.</p>
 *
 * <p>Nothing the snapshot already knows is repeated. Who is playing, in which
 * mode, at what difficulty, and how questions are chosen are all in there, and
 * a second copy could disagree with the first. Only what the snapshot cannot
 * answer is stored alongside it.</p>
 *
 * @param version           the format this was written in
 * @param game              the state of the rules
 * @param tellsCharacterUpFront whether the player told the game their character
 *                          when they chose it, rather than at the end
 * @param openingTurn       who started, so a rematch can start the same way
 * @param firstBoard        which cards the first player had turned face down
 * @param secondBoard       the same for the opposing board
 * @param firstTranscript   the question history down the first player's side
 * @param secondTranscript  the question history down the other
 */
record SavedGame(
        int version,
        GameSnapshot game,
        boolean tellsCharacterUpFront,
        OpeningTurn openingTurn,
        List<Boolean> firstBoard,
        List<Boolean> secondBoard,
        String firstTranscript,
        String secondTranscript) {

    /** The format written today. A file from any other version is discarded. */
    static final int VERSION = 1;

    /** Copies the card lists, so a save cannot change under whoever holds it. */
    SavedGame {
        Objects.requireNonNull(game, "game");
        firstBoard = List.copyOf(Objects.requireNonNull(firstBoard, "firstBoard"));
        secondBoard = List.copyOf(Objects.requireNonNull(secondBoard, "secondBoard"));
    }

    /** True when this was written by the version of the game running now. */
    boolean isReadable() {
        return version == VERSION;
    }
}
