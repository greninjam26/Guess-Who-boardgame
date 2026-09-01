package com.guesswho.ui;

import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The controls for one turn of an online game.
 *
 * <p>Separate from {@link PlayerTurnPanel} rather than a mode inside it. That
 * panel's job is swapping between two people sharing one screen, which is
 * precisely what an online game does not do — each player has their own screen
 * and only ever sees their own board. Folding both into one would mean a panel
 * whose main concern is a thing only half its callers have.</p>
 *
 * <p>Everything shown is decided by the state the server sent. The panel holds
 * no idea of whose turn it is or what has been asked; it is told, every couple
 * of seconds, and shows what that state calls for.</p>
 */
class OnlineTurnPanel {
    /** What a player can do, once they decide to. */
    interface Moves {
        /**
         * @param question what to ask the opponent
         */
        void ask(String question);

        /**
         * @param answer the answer to the question they asked
         */
        void answer(boolean answer);

        /** Opens the board for choosing who to guess at. */
        void guess();
    }

    private final Moves moves;
    private final boolean freeFormQuestions;

    private final JPanel root = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
    private final JLabel prompt = new JLabel();
    private final JComboBox<String> presetQuestions = new JComboBox<>();
    private final JTextField typedQuestion = new JTextField(24);
    private final JButton askButton = new JButton("Ask");
    private final JButton yes = new JButton("Yes");
    private final JButton no = new JButton("No");
    private final JButton guessButton = new JButton("Guess");

    /**
     * @param moves             told what the player wants to do
     * @param freeFormQuestions whether questions are typed rather than chosen
     * @param questions         the board's preset questions
     */
    OnlineTurnPanel(Moves moves, boolean freeFormQuestions, String[] questions) {
        this.moves = moves;
        this.freeFormQuestions = freeFormQuestions;
        presetQuestions.setModel(new DefaultComboBoxModel<>(questions));

        askButton.addActionListener(event -> askWhateverIsChosen());
        //Enter sends a typed question, because reaching for the mouse after
        //typing one feels broken.
        typedQuestion.addActionListener(event -> askWhateverIsChosen());
        yes.addActionListener(event -> moves.answer(true));
        no.addActionListener(event -> moves.answer(false));
        guessButton.addActionListener(event -> moves.guess());

        root.add(prompt);
        root.add(presetQuestions);
        root.add(typedQuestion);
        root.add(askButton);
        root.add(yes);
        root.add(no);
        root.add(guessButton);
    }

    /**
     * @return the panel holding the turn controls
     */
    JPanel panel() {
        return root;
    }

    /**
     * Shows whatever this state calls for.
     *
     * @param state the game as the server described it to this player
     */
    void show(RoomState state) {
        if (state.status() == RoomStatus.WAITING) {
            //Nobody has joined. The code is on the screen behind this one.
            say("Waiting for somebody to join with your code...");
            return;
        }
        if (state.status() == RoomStatus.FINISHED) {
            say(state.winner() == null
                    ? "The game is over."
                    : state.winner().equals(state.you())
                            ? "You won."
                            : state.opponent() + " won.");
            return;
        }
        if (state.yourCharacter() == null) {
            say("Choose the character your opponent has to guess.");
            return;
        }
        if (!state.opponentHasChosen()) {
            say(waitingFor(state, "choose a character"));
            return;
        }
        if (state.questionAwaitingYourAnswer() != null) {
            //Answering comes before anything else: until it is answered
            //neither player can do anything, so nothing else is worth showing.
            say(state.opponent() + " asks: " + state.questionAwaitingYourAnswer());
            showOnly(prompt, yes, no);
            return;
        }
        if (state.yourUnansweredQuestion() != null) {
            say(waitingFor(state, "answer"));
            return;
        }
        if (!state.yourTurn()) {
            say(waitingFor(state, "move"));
            return;
        }
        say("Your turn. Ask a question, or guess.");
        showOnly(prompt, freeFormQuestions ? typedQuestion : presetQuestions,
                askButton, guessButton);
    }

    /**
     * What to say while waiting on the other player.
     *
     * <p>The whole reason the server tracks presence: somebody deliberating and
     * somebody who has closed their laptop produce the same silence, and a
     * player who cannot tell them apart does not know whether to keep waiting.</p>
     *
     * <p>Worded as a suspicion rather than a fact. A phone that went through a
     * tunnel looks exactly like one that was put away, and telling somebody
     * their opponent has left when they are about to answer would be worse than
     * saying nothing.</p>
     */
    private static String waitingFor(RoomState state, String what) {
        if (state.opponentPresent()) {
            return "Waiting for " + state.opponent() + " to " + what + "...";
        }
        return state.opponent() + " seems to have left. Still waiting, in case they come back.";
    }

    private void askWhateverIsChosen() {
        String question = freeFormQuestions
                ? typedQuestion.getText().trim()
                : String.valueOf(presetQuestions.getSelectedItem());
        if (question.isEmpty() || "null".equals(question)) {
            return;
        }
        typedQuestion.setText("");
        moves.ask(question);
    }

    /** Says something and shows nothing else, which is most states. */
    private void say(String message) {
        prompt.setText(message);
        showOnly(prompt);
    }

    private void showOnly(Component... shown) {
        for (Component component : root.getComponents()) {
            component.setVisible(false);
        }
        for (Component component : shown) {
            component.setVisible(true);
        }
        root.revalidate();
        root.repaint();
    }
}
