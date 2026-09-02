package com.guesswho.ui;

import com.guesswho.game.Board;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Everything an online game puts on screen, behind one panel.
 *
 * <p>One panel rather than several the frame has to shuffle: online play has
 * its own sequence — choose, play, finish — and threading it through
 * {@link GUI}'s local-play swapping would put the two flows in the same place
 * without making either clearer.</p>
 *
 * <p>Nothing here decides anything. Every screen is chosen by the state the
 * server sent, so the client cannot show a board that disagrees with the game.</p>
 */
class OnlineGameScreens {
    private static final String CHOOSING = "choosing";
    private static final String PLAYING = "playing";
    private static final String FINISHED = "finished";
    private static final String GONE = "gone";
    private static final String REJOINING = "rejoining";

    private final OnlineGameController controller;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final CharacterBoard chooseFrom;
    private final CharacterBoard yourBoard;
    private final CharacterBoard guessFrom;
    /**
     * Says what a board of twenty-four faces is for at this moment.
     *
     * <p>Guessing swaps one board for another that looks almost identical, and
     * without a word of explanation the swap reads as the game having lost the
     * player's eliminations rather than as a prompt to pick somebody.</p>
     */
    private final JLabel guessPrompt = GuessPrompt.label();
    /**
     * Says the client is still trying, while the server is not answering.
     *
     * <p>A banner over the board rather than a dialog. The game is still there
     * and the turn clock allows for a lapse this long, so there is nothing for
     * the player to do and nothing to interrupt them for — but saying nothing
     * at all would leave a board that has quietly stopped updating.</p>
     */
    private final JLabel connection = new JLabel("", javax.swing.SwingConstants.CENTER);
    private final JLabel goneMessage = new JLabel("", javax.swing.SwingConstants.CENTER);
    /** Holds whichever of the banner and the guess prompt apply right now. */
    private final JPanel northNotices = new JPanel(new java.awt.GridLayout(0, 1));
    private final OnlineTurnPanel turns;
    private final QuestionHistory history = new QuestionHistory();

    private final JLabel choosePrompt = new JLabel("Choose the character your opponent guesses");
    private final JLabel outcome = new JLabel();
    private final JPanel playing = new JPanel(new BorderLayout());

    private boolean guessing;
    private String lastTranscript = "";

    /**
     * @param controller     drives the game
     * @param images         the character portraits
     * @param board          the board, for its preset questions
     * @param freeFormQuestions whether questions are typed rather than chosen
     * @param onFinished     leaves the game once it is over
     */
    OnlineGameScreens(OnlineGameController controller, CharacterImages images, Board board,
            boolean freeFormQuestions, Runnable onFinished) {
        this.controller = controller;

        //The board reports a position; the server wants a name.
        chooseFrom = CharacterBoard.selecting(images, index ->
                controller.chooseCharacter(board.getCharacters().get(index).getName()));
        yourBoard = CharacterBoard.tracking(images);
        guessFrom = CharacterBoard.selecting(images, index -> {
            guessing = false;
            controller.guess(board.getCharacters().get(index).getName());
        });

        turns = new OnlineTurnPanel(new OnlineTurnPanel.Moves() {
            @Override
            public void ask(String question) {
                controller.ask(question);
            }

            @Override
            public void answer(boolean answer) {
                controller.answer(answer);
            }

            @Override
            public void guess() {
                //A separate board, so a player cannot turn a card face down and
                //guess with the same click.
                guessing = true;
                showPlaying();
            }
        }, freeFormQuestions, board.getQuestionsList().stream()
                .map(question -> question.getQuestion()).toArray(String[]::new));

        root.add(choosingCard(), CHOOSING);
        root.add(playing, PLAYING);
        root.add(finishedCard(onFinished), FINISHED);
        root.add(goneCard(onFinished), GONE);
        root.add(rejoiningCard(), REJOINING);
    }

    /**
     * @return the panel holding every online screen
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
        turns.show(state);
        if (state.status() == RoomStatus.FINISHED) {
            outcome.setText(state.winner() == null
                    ? "The game is over."
                    : state.winner().equals(state.you())
                            ? "You won."
                            : state.opponent() + " won.");
            cards.show(root, FINISHED);
            return;
        }
        if (state.yourCharacter() == null) {
            cards.show(root, CHOOSING);
            return;
        }
        updateTranscript(state);
        showPlaying();
    }

    /**
     * Rewrites the transcript from the state.
     *
     * <p>Rebuilt rather than appended to: the state is the whole history every
     * time, and appending would double every line the poll delivers twice.</p>
     */
    private void updateTranscript(RoomState state) {
        StringBuilder yours = new StringBuilder();
        for (RoomState.AskedQuestion asked : state.yourQuestions()) {
            yours.append(LabelText.escaped(asked.question()))
                    .append(asked.answer() ? " — Yes<br>" : " — No<br>");
        }
        StringBuilder theirs = new StringBuilder();
        for (RoomState.AskedQuestion asked : state.opponentQuestions()) {
            theirs.append(LabelText.escaped(asked.question()))
                    .append(asked.answer() ? " — Yes<br>" : " — No<br>");
        }
        String transcript = yours + "|" + theirs;
        if (transcript.equals(lastTranscript)) {
            return;
        }
        lastTranscript = transcript;
        history.restore(yours.toString(), theirs.toString());
    }

    private void showPlaying() {
        playing.removeAll();
        northNotices.removeAll();
        if (!connection.getText().isEmpty()) {
            northNotices.add(connection);
        }
        if (guessing) {
            //Carried over from the tracking board this player was just looking
            //at. A guess board that started clean threw away a game's worth of
            //eliminating at the moment it mattered most.
            guessFrom.showRuledOut(yourBoard.faceDownCards());
            northNotices.add(guessPrompt);
        }
        if (northNotices.getComponentCount() > 0) {
            playing.add(northNotices, BorderLayout.NORTH);
        }
        playing.add(guessing ? guessFrom : yourBoard, BorderLayout.CENTER);
        playing.add(turns.panel(), BorderLayout.SOUTH);
        playing.add(history.firstPanel(), BorderLayout.EAST);
        playing.add(history.secondPanel(), BorderLayout.WEST);
        cards.show(root, PLAYING);
        playing.revalidate();
        playing.repaint();
    }

    /**
     * Shows that this client is picking a game back up.
     *
     * <p>Held until the first poll answers. Without it a rejoining client shows
     * whichever card the layout happens to start on — the character chooser —
     * which invites a player to choose a character they chose yesterday.</p>
     */
    void showRejoining() {
        cards.show(root, REJOINING);
    }

    private JPanel rejoiningCard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        panel.add(new JLabel("Picking your game back up…",
                javax.swing.SwingConstants.CENTER), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Shows or clears the reconnecting banner.
     *
     * @param trying whether the client is currently unable to reach the server
     */
    void showConnectionTrouble(boolean trying) {
        connection.setText(trying
                ? "Reconnecting… your game is safe, and this client is still trying."
                : "");
        //Only redraws the board, so a banner appearing does not disturb a
        //player who is part-way through choosing or reading the ending.
        if (root.isShowing() || playing.getParent() != null) {
            showPlaying();
        }
    }

    /**
     * Shows that the room has gone for good.
     *
     * @param message what to tell the player
     */
    void showGone(String message) {
        goneMessage.setText(message);
        cards.show(root, GONE);
    }

    private JPanel goneCard(Runnable onFinished) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        goneMessage.setFont(goneMessage.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(goneMessage, BorderLayout.NORTH);
        javax.swing.JButton done = new javax.swing.JButton("Back to the menu");
        done.addActionListener(event -> {
            controller.leave();
            onFinished.run();
        });
        panel.add(done, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel choosingCard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(choosePrompt, BorderLayout.NORTH);
        panel.add(chooseFrom, BorderLayout.CENTER);
        return panel;
    }

    private JPanel finishedCard(Runnable onFinished) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        outcome.setFont(outcome.getFont().deriveFont(Font.BOLD, 20f));
        panel.add(outcome, BorderLayout.NORTH);
        javax.swing.JButton done = new javax.swing.JButton("Back to the menu");
        done.addActionListener(event -> {
            controller.leave();
            onFinished.run();
        });
        panel.add(done, BorderLayout.SOUTH);
        return panel;
    }
}
