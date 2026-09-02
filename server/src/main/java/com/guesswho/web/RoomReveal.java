package com.guesswho.web;

import com.guesswho.game.AnswerCorrection;
import com.guesswho.game.Board;
import com.guesswho.game.Character;
import com.guesswho.game.CharacterCommitment;
import com.guesswho.game.GameSnapshot;
import com.guesswho.game.Question;
import com.guesswho.persistence.RoomRepository;
import com.guesswho.room.GameReveal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns a finished room into what both players may finally see.
 *
 * <p>The counterpart to {@link RoomProjection}: that one shows a game in
 * progress to one player and is built so it cannot carry the opponent's
 * character, this one shows a game that is over and exists to carry exactly
 * that. Splitting them is what lets the first guarantee be structural.</p>
 */
@Component
class RoomReveal {
    private final Board board;

    /**
     * @throws IllegalStateException if the board's data cannot be read, which
     *         would mean the server could not referee a game either
     */
    RoomReveal() {
        try {
            //Once, at startup. The reveal needs the board to say what each
            //question's answer should have been, and loading the CSVs per
            //request would be work repeated for data that never changes.
            this.board = new Board();
        }
        catch (Exception unloadable) {
            throw new IllegalStateException("The board could not be loaded", unloadable);
        }
    }

    /**
     * Reveals a finished game to one of the two people who played it.
     *
     * @param room      the finished room
     * @param accountId who is asking
     * @return both characters, both promises, and how the answers held up
     */
    GameReveal forPlayer(RoomRepository.StoredRoom room, long accountId) {
        GameSnapshot game = RoomService.deserialise(room.gameState());
        boolean asking = room.hostAccountId() == accountId;
        GameSnapshot.PlayerState yours = asking ? game.firstPlayer() : game.secondPlayer();
        GameSnapshot.PlayerState theirs = asking ? game.secondPlayer() : game.firstPlayer();
        return new GameReveal(
                room.code(),
                game.winner(),
                //Each player is judged by the answers the other one wrote down.
                //A question lives on the asker's list along with the answer they
                //were given, so reviewing somebody's honesty means reading their
                //opponent's transcript against their character.
                verify(yours, theirs),
                verify(theirs, yours));
    }

    private GameReveal.Verified verify(
            GameSnapshot.PlayerState player, GameSnapshot.PlayerState opponent) {
        boolean promised = player.commitmentHash() != null && player.commitmentNonce() != null;
        boolean kept = promised
                && new CharacterCommitment(player.commitmentHash(), player.commitmentNonce())
                        .matches(player.selectedCharacter());
        return new GameReveal.Verified(
                player.username(),
                player.selectedCharacter(),
                promised,
                kept,
                wrongAnswers(player.selectedCharacter(), opponent));
    }

    /**
     * The answers this player gave that their own character contradicts.
     *
     * <p>Read from the opponent's transcript, which is where an answer is
     * recorded: the asker keeps the question and what they were told.</p>
     *
     * <p>A question the board no longer recognises is skipped rather than
     * counted against anybody. Free-form questions resolve to a board question
     * when they are asked, so this should not happen — and if it ever does, the
     * honest answer is that this one cannot be checked, not that the player
     * lied.</p>
     */
    private List<AnswerCorrection> wrongAnswers(
            String characterName, GameSnapshot.PlayerState opponent) {
        if (characterName == null) {
            //No character means nothing to check against. A game cannot finish
            //this way today, and inventing a verdict would be worse than none.
            return List.of();
        }
        Character character = board.getCharacters().stream()
                .filter(each -> each.getName().equals(characterName))
                .findFirst()
                .orElse(null);
        if (character == null) {
            return List.of();
        }
        List<AnswerCorrection> wrong = new ArrayList<>();
        for (int index = 0; index < opponent.questionsAsked().size()
                && index < opponent.questionAnswers().size(); index++) {
            Question question = board.findQuestion(opponent.questionsAsked().get(index));
            if (question == null) {
                continue;
            }
            boolean expected = board.getAnswers()
                    [character.getCharacterIndex()][question.getQuestionIndex()];
            if (expected != opponent.questionAnswers().get(index)) {
                wrong.add(new AnswerCorrection(question.getQuestion(), expected));
            }
        }
        return wrong;
    }
}
