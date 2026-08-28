package com.guesswho.web;

import com.guesswho.game.GameResult;
import com.guesswho.persistence.GameResultRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts completed games from Guess Who clients.
 */
@RestController
@RequestMapping("/api/game-results")
public class GameResultController {
    private final GameResultRepository gameResultRepository;

    /**
     * Creates a controller backed by the configured result repository.
     *
     * @param gameResultRepository repository used to persist submissions
     */
    public GameResultController(GameResultRepository gameResultRepository) {
        this.gameResultRepository = gameResultRepository;
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
        for (GameResult.Participant participant : gameResult.participants()) {
            if (participant == null || isBlank(participant.name())
                    || isBlank(participant.selectedCharacter())
                    || participant.questionAnswers() == null) {
                throw incompleteResult();
            }
            for (GameResult.QuestionAnswer questionAnswer : participant.questionAnswers()) {
                if (questionAnswer == null || isBlank(questionAnswer.question())) {
                    throw incompleteResult();
                }
            }
        }
        boolean winnerIsParticipant = gameResult.participants().stream()
                .anyMatch(participant -> participant.name().equals(gameResult.winner()));
        if (!winnerIsParticipant) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Winner must match a participant");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException incompleteResult() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Game result fields must not be blank");
    }
}
