package com.guesswho.ui;

import com.guesswho.room.Room;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Opening a game for a friend, or joining theirs.
 *
 * <p>No public matchmaking. A six-character code passed to somebody you know is
 * the whole of it, which is why the code is shown large enough to read out and
 * why joining accepts it in whatever shape it was typed.</p>
 */
class OnlineRoomScreen implements OnlineGameController.View {
    /** Told when both players are in and there is a game to show. */
    @FunctionalInterface
    interface Completion {
        /**
         * @param state the game as it stands, for the board to render
         */
        void gameReady(RoomState state);
    }

    private static final String CHOICE = "choice";
    private static final String JOINING = "joining";
    private static final String WAITING = "waiting";

    private final OnlineGameController controller;
    private final Completion completion;
    private final Runnable onBack;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final JTextField codeField = new JTextField(10);
    private final JLabel sharedCode = new JLabel();
    private final JLabel waitingMessage = new JLabel();
    private final JLabel choiceMessage = new JLabel(" ");
    private final JLabel joinMessage = new JLabel(" ");

    private boolean handedOver;

    /**
     * @param controller drives the game once a room exists
     * @param completion told when there is a game to show
     * @param onBack     leaves online play altogether
     */
    OnlineRoomScreen(OnlineGameController controller, Completion completion, Runnable onBack) {
        this.controller = controller;
        this.completion = completion;
        this.onBack = onBack;

        root.add(choiceCard(), CHOICE);
        root.add(joinCard(), JOINING);
        root.add(waitingCard(), WAITING);
    }

    /**
     * @return the panel holding the room screens
     */
    JPanel panel() {
        return root;
    }

    /** Starts again at the choice between opening and joining. */
    void begin() {
        handedOver = false;
        choiceMessage.setText(" ");
        joinMessage.setText(" ");
        codeField.setText("");
        cards.show(root, CHOICE);
    }

    @Override
    public void roomOpened(Room room) {
        //Shown large: this is read off one screen and typed into another.
        sharedCode.setText(room.code());
        waitingMessage.setText(room.guestName() == null
                ? "Share this code. Waiting for somebody to join..."
                : "Playing against " + room.guestName());
        cards.show(root, WAITING);
    }

    @Override
    public void stateChanged(RoomState state) {
        if (state.status() == RoomStatus.WAITING) {
            waitingMessage.setText("Share this code. Waiting for somebody to join...");
            return;
        }
        if (handedOver) {
            //The board has it from here. Handing over twice would rebuild the
            //screen underneath somebody mid-move.
            return;
        }
        handedOver = true;
        completion.gameReady(state);
    }

    @Override
    public void problem(String message) {
        //Whichever screen they are on is the one that has to say it.
        choiceMessage.setText(message);
        joinMessage.setText(message);
        waitingMessage.setText(message);
    }

    @Override
    public void connectionLost() {
        //The host waiting for somebody to join is polling too, so this screen
        //hits the same run of failures the board does. Said in the same place
        //as everything else here rather than as a dialog over the code they are
        //trying to read out.
        problem("Reconnecting… the server is not answering just now.");
    }

    @Override
    public void connectionRestored() {
        problem("");
    }

    @Override
    public void revealed(com.guesswho.room.GameReveal reveal) {
        //A game only ends on the board, never on the room screen: by the time
        //there is an ending to reveal, the board has been showing for a while.
    }

    @Override
    public void cannotContinue(String message) {
        problem(message);
    }

    @Override
    public void signedOut() {
        problem("You have been signed out. Sign in again to play online.");
    }

    private JPanel choiceCard() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        panel.add(new JLabel("Play against a friend"));

        JButton create = new JButton("Start a game and get a code");
        JButton join = new JButton("Join with a code");
        JButton back = new JButton("Back");
        create.addActionListener(event -> {
            choiceMessage.setText("Opening a game...");
            controller.createRoom(this);
        });
        join.addActionListener(event -> {
            joinMessage.setText(" ");
            cards.show(root, JOINING);
        });
        back.addActionListener(event -> onBack.run());

        panel.add(create);
        panel.add(join);
        panel.add(back);
        choiceMessage.setForeground(Color.RED.darker());
        panel.add(choiceMessage);
        return panel;
    }

    private JPanel joinCard() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        panel.add(new JLabel("Enter the code your friend gave you"));
        panel.add(codeField);

        JButton join = new JButton("Join");
        JButton back = new JButton("Back");
        join.addActionListener(event -> joinTypedCode());
        //Enter joins, because a code field is somewhere people expect it to.
        codeField.addActionListener(event -> joinTypedCode());
        back.addActionListener(event -> cards.show(root, CHOICE));

        panel.add(join);
        panel.add(back);
        joinMessage.setForeground(Color.RED.darker());
        panel.add(joinMessage);
        return panel;
    }

    private JPanel waitingCard() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        panel.add(new JLabel("Your code"));
        sharedCode.setFont(sharedCode.getFont().deriveFont(Font.BOLD, 32f));
        panel.add(sharedCode);
        panel.add(waitingMessage);

        JButton leave = new JButton("Leave");
        leave.addActionListener(event -> {
            controller.leave();
            onBack.run();
        });
        panel.add(leave);
        return panel;
    }

    private void joinTypedCode() {
        String typed = codeField.getText().trim();
        if (typed.isEmpty()) {
            joinMessage.setText("Enter the code");
            return;
        }
        joinMessage.setText("Joining...");
        controller.joinRoom(typed, this);
    }
}
