package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.awt.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OnlineTurnPanelTest {
    private final List<String> asked = new ArrayList<>();
    private final List<Boolean> answered = new ArrayList<>();
    private final List<String> guesses = new ArrayList<>();

    private OnlineTurnPanel panel;

    @BeforeEach
    void freshPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel = new OnlineTurnPanel(
                new OnlineTurnPanel.Moves() {
                    @Override
                    public void ask(String question) {
                        asked.add(question);
                    }

                    @Override
                    public void answer(boolean answer) {
                        answered.add(answer);
                    }

                    @Override
                    public void guess() {
                        guesses.add("opened");
                    }
                },
                false,
                new String[] {"Does your character wear glasses?", "Is the person wearing a hat?"}));
    }

    @Test
    void waitsForSomebodyToJoin() throws Exception {
        show(waiting());

        assertTrue(promptText().contains("Waiting for somebody to join"));
        assertFalse(isShown("Ask"));
    }

    @Test
    void asksThePlayerToChooseBeforeAnythingElse() throws Exception {
        show(playing().withYourCharacter(null));

        assertTrue(promptText().contains("Choose the character"));
        assertFalse(isShown("Ask"));
    }

    @Test
    void waitsForTheOpponentToChoose() throws Exception {
        show(playing().withOpponentHasChosen(false));

        assertTrue(promptText().contains("choose a character"));
        assertFalse(isShown("Ask"));
    }

    @Test
    void offersAQuestionAndAGuessOnYourTurn() throws Exception {
        show(playing());

        assertTrue(isShown("Ask"));
        assertTrue(isShown("Guess"));
        assertFalse(isShown("Yes"));
    }

    @Test
    void showsOnlyYesAndNoWhenYouOweAnAnswer() throws Exception {
        //Until it is answered neither player can do anything else, so nothing
        //else is worth putting on screen.
        show(playing().withQuestionAwaitingYourAnswer("Does your character wear glasses?"));

        assertTrue(isShown("Yes"));
        assertTrue(isShown("No"));
        assertFalse(isShown("Ask"));
        assertFalse(isShown("Guess"));
        assertTrue(promptText().contains("Does your character wear glasses?"));
    }

    @Test
    void waitsWhileYourOwnQuestionIsUnanswered() throws Exception {
        show(playing().withYourUnansweredQuestion("Does your character wear glasses?"));

        assertTrue(promptText().contains("to answer"));
        assertFalse(isShown("Ask"), "Asking again while one is outstanding is refused anyway");
    }

    @Test
    void waitsWhenItIsNotYourTurn() throws Exception {
        show(playing().withYourTurn(false));

        assertTrue(promptText().contains("to move"));
        assertFalse(isShown("Ask"));
    }

    @Test
    void saysWhoWon() throws Exception {
        show(finished("host"));
        assertTrue(promptText().contains("You won"));

        show(finished("guest"));
        assertTrue(promptText().contains("guest won"));
    }

    @Test
    void saysNothingUnusualWhileTheOpponentIsStillThere() throws Exception {
        show(playing().withYourTurn(false));

        assertTrue(promptText().contains("to move"));
        assertFalse(promptText().contains("seems to have left"));
    }

    @Test
    void saysWhenTheOpponentSeemsToHaveGone() throws Exception {
        //The point of tracking presence: silence from somebody thinking and
        //silence from somebody who quit look identical without this.
        show(playing().withYourTurn(false).withOpponentPresent(false));

        assertTrue(promptText().contains("seems to have left"), promptText());
    }

    @Test
    void saysItAsASuspicionRatherThanAFact() throws Exception {
        //A phone that went through a tunnel looks like one that was put away.
        show(playing().withYourTurn(false).withOpponentPresent(false));

        assertTrue(promptText().contains("in case they come back"), promptText());
    }

    @Test
    void saysItWhicheverWayTheGameIsWaiting() throws Exception {
        //All three waiting states, because one of them forgetting is how a
        //player ends up staring at a screen that tells them nothing.
        for (Playing waiting : List.of(
                playing().withOpponentHasChosen(false).withOpponentPresent(false),
                playing().withYourUnansweredQuestion("Do they wear glasses?")
                        .withOpponentPresent(false),
                playing().withYourTurn(false).withOpponentPresent(false))) {
            show(waiting);

            assertTrue(promptText().contains("seems to have left"), promptText());
        }
    }

    @Test
    void stillOffersYourOwnTurnWhenTheOpponentHasGone() throws Exception {
        //Their absence does not stop you playing: you can still guess, which is
        //the one move that ends a game nobody else is going to finish.
        show(playing().withOpponentPresent(false));

        assertTrue(isShown("Guess"));
        assertTrue(isShown("Ask"));
    }

    @Test
    void sendsTheChosenQuestion() throws Exception {
        show(playing());

        SwingUtilities.invokeAndWait(() -> button("Ask").doClick());

        assertEquals(List.of("Does your character wear glasses?"), asked);
    }

    @Test
    void sendsTheAnswerTheyPressed() throws Exception {
        show(playing().withQuestionAwaitingYourAnswer("Does your character wear glasses?"));

        SwingUtilities.invokeAndWait(() -> button("Yes").doClick());
        SwingUtilities.invokeAndWait(() -> button("No").doClick());

        assertEquals(List.of(true, false), answered);
    }

    @Test
    void opensTheBoardToGuess() throws Exception {
        show(playing());

        SwingUtilities.invokeAndWait(() -> button("Guess").doClick());

        assertEquals(1, guesses.size());
    }

    @Test
    void showsAWholeGameWithoutEverShowingTwoThingsAtOnce() throws Exception {
        //Every state, in order. The panel is driven entirely by what the server
        //said, so a state it mishandles puts the wrong controls on screen
        //rather than throwing.
        for (RoomState state : List.of(
                waiting(),
                playing().withYourCharacter(null).state(),
                playing().withOpponentHasChosen(false).state(),
                playing().state(),
                playing().withQuestionAwaitingYourAnswer(
                        "Does your character wear glasses?").state(),
                playing().withYourUnansweredQuestion(
                        "Does your character wear glasses?").state(),
                playing().withYourTurn(false).state(),
                finished("host"))) {
            show(state);

            assertFalse(isShown("Ask") && isShown("Yes"),
                    "Asking and answering were offered at the same time");
        }
    }

    // --- helpers -------------------------------------------------------

    private void show(RoomState state) throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.show(state));
    }

    private void show(Playing playing) throws Exception {
        show(playing.state());
    }

    private String promptText() {
        for (Component component : panel.panel().getComponents()) {
            if (component instanceof JLabel label && component.isVisible()) {
                return label.getText();
            }
        }
        return "";
    }

    private boolean isShown(String label) {
        JButton found = findButton(label);
        return found != null && found.isVisible();
    }

    private JButton button(String label) {
        JButton found = findButton(label);
        if (found == null) {
            throw new AssertionError("No button labelled " + label);
        }
        return found;
    }

    private JButton findButton(String label) {
        for (Component component : panel.panel().getComponents()) {
            if (component instanceof JButton candidate && label.equals(candidate.getText())) {
                return candidate;
            }
        }
        return null;
    }

    private static RoomState waiting() {
        return RoomState.builder()
                .code("BCDFGH")
                .status(RoomStatus.WAITING)
                .you("host")
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }

    private static RoomState finished(String winner) {
        return RoomState.builder()
                .code("BCDFGH")
                .status(RoomStatus.FINISHED)
                .you("host")
                .opponent("guest")
                .yourCharacter("Olivia")
                .opponentHasChosen(true)
                .opponentPresent(true)
                .winner(winner)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }

    private static Playing playing() {
        return new Playing("Olivia", true, true, true, null, null);
    }

    /** A state under construction, so each test can vary one thing about it. */
    private record Playing(
            String yourCharacter,
            boolean opponentHasChosen,
            boolean opponentPresent,
            boolean yourTurn,
            String questionAwaitingYourAnswer,
            String yourUnansweredQuestion) {

        Playing withYourCharacter(String character) {
            return new Playing(character, opponentHasChosen, opponentPresent, yourTurn,
                    questionAwaitingYourAnswer, yourUnansweredQuestion);
        }

        Playing withOpponentHasChosen(boolean chosen) {
            return new Playing(yourCharacter, chosen, opponentPresent, yourTurn,
                    questionAwaitingYourAnswer, yourUnansweredQuestion);
        }

        Playing withOpponentPresent(boolean present) {
            return new Playing(yourCharacter, opponentHasChosen, present, yourTurn,
                    questionAwaitingYourAnswer, yourUnansweredQuestion);
        }

        Playing withYourTurn(boolean turn) {
            return new Playing(yourCharacter, opponentHasChosen, opponentPresent, turn,
                    questionAwaitingYourAnswer, yourUnansweredQuestion);
        }

        Playing withQuestionAwaitingYourAnswer(String question) {
            return new Playing(yourCharacter, opponentHasChosen, opponentPresent, yourTurn,
                    question, yourUnansweredQuestion);
        }

        Playing withYourUnansweredQuestion(String question) {
            return new Playing(yourCharacter, opponentHasChosen, opponentPresent, yourTurn,
                    questionAwaitingYourAnswer, question);
        }

        RoomState state() {
            return RoomState.builder()
                    .code("BCDFGH")
                    .status(RoomStatus.IN_PROGRESS)
                    .you("host")
                    .opponent("guest")
                    .yourCharacter(yourCharacter)
                    .opponentHasChosen(opponentHasChosen)
                    .opponentPresent(opponentPresent)
                    .yourTurn(yourTurn)
                    .currentPlayer(yourTurn ? "host" : "guest")
                    .questionAwaitingYourAnswer(questionAwaitingYourAnswer)
                    .yourUnansweredQuestion(yourUnansweredQuestion)
                    .expiresAt(Instant.now().plusSeconds(600))
                    .build();
        }
    }
}
