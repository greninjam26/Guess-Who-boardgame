package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.QuestionMode;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.JTextField;

/**
 * Everything the player answers before a game begins: the welcome screen, the
 * mode choice, names and birthdays, and who takes the first turn.
 *
 * <p>The screens sit on a {@link CardLayout} and advance themselves, so the
 * interface no longer adds and removes panels from the frame to move between
 * setup steps. Collected answers go into the shared {@link GameSetup}, and the
 * completion callback fires once there is nothing left to ask.</p>
 */
class SetupScreens {
    /** Notified when the player has answered everything. */
    @FunctionalInterface
    interface Completion {
        /**
         * Called once setup is finished.
         *
         * @param openingTurn who the player chose to take the first turn
         */
        void setupComplete(OpeningTurn openingTurn);
    }

    private static final String WELCOME = "welcome";
    private static final String MODE = "mode";
    private static final String FIRST_NAME = "firstName";
    private static final String FIRST_BIRTHDAY = "firstBirthday";
    private static final String SECOND_NAME = "secondName";
    private static final String SECOND_BIRTHDAY = "secondBirthday";
    private static final String OPENING_TURN = "openingTurn";

    private final GameSetup setup;
    private final Completion completion;
    private final Consumer<String> errorReporter;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final JTextField firstNameField = new JTextField(20);
    private final JTextField firstBirthdayField = new JTextField(20);
    private final JTextField secondNameField = new JTextField(20);
    private final JTextField secondBirthdayField = new JTextField(20);

    private final JButton firstPlayerStarts = new JButton();
    private final JButton secondPlayerStarts = new JButton();
    private final JButton computerStarts = new JButton("AI goes first");
    private final JButton randomStarts = new JButton("Randomly choose who go first");
    private final JButton youngerStarts = new JButton("Younger person go first");
    private final JPanel openingTurnPanel = new JPanel();

    /**
     * Builds the setup screens.
     *
     * @param setup collects the player's answers
     * @param errorReporter shows a message when an answer cannot be accepted
     * @param completion notified once setup is finished
     */
    SetupScreens(GameSetup setup, Consumer<String> errorReporter, Completion completion) {
        this.setup = setup;
        this.errorReporter = errorReporter;
        this.completion = completion;

        root.add(welcomeCard(), WELCOME);
        root.add(modeCard(), MODE);
        root.add(nameCard(
                "Please enter your username (you have been warned don't make the username too long): ",
                firstNameField, this::acceptFirstName), FIRST_NAME);
        root.add(birthdayCard(firstBirthdayField, this::acceptFirstBirthday), FIRST_BIRTHDAY);
        root.add(nameCard(
                "Second player, please enter your username"
                        + " (please don't enter the same username as the first player): ",
                secondNameField, this::acceptSecondName), SECOND_NAME);
        root.add(birthdayCard(secondBirthdayField, this::acceptSecondBirthday), SECOND_BIRTHDAY);
        root.add(openingTurnCard(), OPENING_TURN);
    }

    /**
     * Returns the panel holding every setup screen.
     *
     * @return the setup panel
     */
    JPanel panel() {
        return root;
    }

    private JPanel welcomeCard() {
        JPanel panel = new JPanel();
        JLabel instructions = new JLabel(SetupText.INSTRUCTIONS);
        instructions.setVisible(false);
        JButton howToPlay = new JButton("How To Play");
        JButton start = new JButton("Start The Game");
        howToPlay.addActionListener(event -> instructions.setVisible(true));
        start.addActionListener(event -> cards.show(root, MODE));
        panel.add(new JLabel("Welcome to the Guess Who? Board Game!!"));
        panel.add(howToPlay);
        panel.add(start);
        panel.add(instructions);
        return panel;
    }

    private JPanel modeCard() {
        JPanel panel = new JPanel();
        panel.add(new JLabel("Please choose your game mode: "));
        panel.add(modeButton("player vs computer easy mode",
                () -> setup.againstComputer(ComputerDifficulty.EASY)));
        panel.add(modeButton("player vs computer hard mode",
                () -> setup.againstComputer(ComputerDifficulty.HARD)));
        panel.add(modeButton("player vs player preset questions",
                () -> setup.againstPlayer(QuestionMode.PRESET)));
        panel.add(modeButton("player vs player ask questions",
                () -> setup.againstPlayer(QuestionMode.FREE_FORM)));
        return panel;
    }

    private JButton modeButton(String text, Runnable choose) {
        JButton button = new JButton(text);
        button.addActionListener(event -> {
            choose.run();
            prepareOpeningTurnChoices();
            cards.show(root, FIRST_NAME);
        });
        return button;
    }

    private JPanel nameCard(String prompt, JTextField field, Runnable accept) {
        JPanel panel = new JPanel();
        JButton confirm = new JButton("Comfirm");
        confirm.addActionListener(event -> accept.run());
        panel.add(new JLabel(prompt));
        panel.add(field);
        panel.add(confirm);
        return panel;
    }

    private JPanel birthdayCard(JTextField field, Runnable accept) {
        JPanel panel = new JPanel();
        JButton confirm = new JButton("Comfirm");
        confirm.addActionListener(event -> accept.run());
        panel.add(new JLabel("Please enter your birthday in the form of(YYYYMMDD): "));
        panel.add(field);
        panel.add(confirm);
        return panel;
    }

    private JPanel openingTurnCard() {
        JPanel panel = new JPanel(new BorderLayout());
        // A label stretches to the full width in a BorderLayout region and would
        // otherwise sit against the left edge, unlike the flowed cards.
        JLabel prompt = new JLabel("Please choice who do you want to do first or just random: ");
        prompt.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(prompt, BorderLayout.NORTH);
        panel.add(openingTurnPanel, BorderLayout.CENTER);
        firstPlayerStarts.addActionListener(event -> completion.setupComplete(OpeningTurn.FIRST_PLAYER));
        secondPlayerStarts.addActionListener(event -> completion.setupComplete(OpeningTurn.SECOND_PLAYER));
        computerStarts.addActionListener(event -> completion.setupComplete(OpeningTurn.COMPUTER));
        randomStarts.addActionListener(event -> completion.setupComplete(OpeningTurn.RANDOM));
        youngerStarts.addActionListener(event -> completion.setupComplete(OpeningTurn.YOUNGER));
        return panel;
    }

    /** Only the choices that make sense for the chosen mode are offered. */
    private void prepareOpeningTurnChoices() {
        openingTurnPanel.removeAll();
        openingTurnPanel.add(firstPlayerStarts);
        if (setup.isAgainstComputer()) {
            openingTurnPanel.add(computerStarts);
        }
        else {
            openingTurnPanel.add(secondPlayerStarts);
            openingTurnPanel.add(youngerStarts);
        }
        openingTurnPanel.add(randomStarts);
    }

    private void acceptFirstName() {
        String username = firstNameField.getText();
        if (username == null || username.isBlank()) {
            errorReporter.accept("Username must not be blank.");
            return;
        }
        if (setup.isAgainstComputer() && username.equals("AI")) {
            errorReporter.accept("AI is reserved for the computer player.");
            return;
        }
        setup.firstUsername(username);
        firstPlayerStarts.setText(username + " goes first");
        cards.show(root, setup.isAgainstPlayer() ? FIRST_BIRTHDAY : OPENING_TURN);
    }

    private void acceptFirstBirthday() {
        readBirthday(firstBirthdayField).ifPresent(birthday -> {
            setup.firstBirthday(birthday);
            cards.show(root, SECOND_NAME);
        });
    }

    private void acceptSecondName() {
        String username = secondNameField.getText();
        if (username == null || username.isBlank()) {
            errorReporter.accept("Username must not be blank.");
            return;
        }
        if (username.equals(setup.firstUsername())) {
            errorReporter.accept("Player usernames must be different.");
            return;
        }
        setup.secondUsername(username);
        secondPlayerStarts.setText(username + " goes first");
        cards.show(root, SECOND_BIRTHDAY);
    }

    private void acceptSecondBirthday() {
        readBirthday(secondBirthdayField).ifPresent(birthday -> {
            setup.secondBirthday(birthday);
            cards.show(root, OPENING_TURN);
        });
    }

    /** Reports bad input instead of throwing, which is what parsing used to do. */
    private java.util.Optional<Integer> readBirthday(JTextField field) {
        try {
            return java.util.Optional.of(Integer.parseInt(field.getText().trim()));
        }
        catch (NumberFormatException exception) {
            errorReporter.accept("Birthday must be digits in the form YYYYMMDD.");
            return java.util.Optional.empty();
        }
    }
}
