package com.guesswho.web;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.GameResult;
import com.guesswho.game.QuestionMode;
import com.guesswho.persistence.GameResultHistoryRepository;
import com.guesswho.persistence.GameResultRepository;
import com.guesswho.persistence.StoredGameResult;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts completed games from Guess Who clients.
 */
@RestController
@RequestMapping("/api/game-results")
public class GameResultController {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    //These match the column widths in V1__baseline.sql. Without them an
    //oversized field reaches the database, breaks the constraint there, and
    //surfaces as a 500 — the server reporting its own error for what is
    //plainly a bad request. Change one of these and change the migration.
    private static final int MAX_NAME = 255;
    private static final int MAX_QUESTION = 2000;

    private final GameResultRepository gameResultRepository;
    private final GameResultHistoryRepository gameResultHistoryRepository;

    /**
     * Creates a controller backed by the configured result repository.
     *
     * @param gameResultRepository repository used to persist submissions
     * @param gameResultHistoryRepository repository used to read saved games
     */
    public GameResultController(
            GameResultRepository gameResultRepository,
            GameResultHistoryRepository gameResultHistoryRepository) {
        this.gameResultRepository = gameResultRepository;
        this.gameResultHistoryRepository = gameResultHistoryRepository;
    }

    /**
     * Returns one page of saved game results, newest first.
     *
     * @param limit maximum number of games to return
     * @param offset number of games to skip
     * @return completed-game history
     */
    @GetMapping
    public List<GameResultHistoryResponse> getGameResults(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(defaultValue = "0") int offset) {
        requireRange("limit", limit, 1, MAX_LIMIT);
        requireRange("offset", offset, 0, Integer.MAX_VALUE);
        return gameResultHistoryRepository.findPage(limit, offset).stream()
                .map(GameResultHistoryResponse::from)
                .toList();
    }

    private void requireRange(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    name + " must be between " + minimum + " and " + maximum);
        }
    }

    /**
     * Stores one completed game result.
     *
     * @param gameResult completed game submitted by a client
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createGameResult(@RequestBody GameResult gameResult) {
        validate(gameResult);
        gameResultRepository.save(gameResult);
    }

    private void validate(GameResult gameResult) {
        if (gameResult == null || gameResult.participants() == null
                || gameResult.participants().isEmpty() || isBlank(gameResult.winner())) {
            throw incompleteResult();
        }
        if (gameResult.mode() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Game mode must be supplied");
        }
        if (gameResult.questionMode() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Question mode must be supplied");
        }
        requireAtMost("winner", gameResult.winner(), MAX_NAME);
        for (GameResult.Participant participant : gameResult.participants()) {
            if (participant == null || isBlank(participant.name())
                    || isBlank(participant.selectedCharacter())
                    || participant.questionAnswers() == null) {
                throw incompleteResult();
            }
            requireAtMost("Participant name", participant.name(), MAX_NAME);
            requireAtMost("Selected character", participant.selectedCharacter(), MAX_NAME);
            for (GameResult.QuestionAnswer questionAnswer : participant.questionAnswers()) {
                if (questionAnswer == null || isBlank(questionAnswer.question())) {
                    throw incompleteResult();
                }
                requireAtMost("Question", questionAnswer.question(), MAX_QUESTION);
            }
        }
        boolean winnerIsParticipant = gameResult.participants().stream()
                .anyMatch(participant -> participant.name().equals(gameResult.winner()));
        if (!winnerIsParticipant) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Winner must match a participant");
        }
    }

    private void requireAtMost(String name, String value, int maximum) {
        if (value.length() > maximum) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    name + " must be at most " + maximum + " characters");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException incompleteResult() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Game result fields must not be blank");
    }

    /**
     * Public representation of one saved game result.
     *
     * @param id database identity
     * @param createdAt time the result was stored
     * @param participants participants in play order
     * @param winner winning participant name
     * @param mode how the game was played
     * @param difficulty computer difficulty, or {@code null} outside a
     *        player-versus-computer game
     * @param questionMode how questions were chosen during the game
     */
    public record GameResultHistoryResponse(
            long id,
            LocalDateTime createdAt,
            List<GameResult.Participant> participants,
            String winner,
            GameMode mode,
            ComputerDifficulty difficulty,
            QuestionMode questionMode) {
        private static GameResultHistoryResponse from(StoredGameResult storedGameResult) {
            GameResult gameResult = storedGameResult.gameResult();
            return new GameResultHistoryResponse(
                    storedGameResult.id(),
                    storedGameResult.createdAt(),
                    gameResult.participants(),
                    gameResult.winner(),
                    gameResult.mode(),
                    gameResult.difficulty(),
                    gameResult.questionMode());
        }
    }
}
