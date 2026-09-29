package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.QuestionMode;

import java.awt.CardLayout;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
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
    private final Runnable onlineChosen;
    private final Consumer<String> errorReporter;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final JTextField firstNameField = new JTextField(20);
    private final JTextField firstBirthdayField = new JTextField(20);
    private final JTextField secondNameField = new JTextField(20);
    private final JTextField secondBirthdayField = new JTextField(20);

    private final JButton firstPlayerStarts = UiTheme.choiceButton("");
    private final JButton secondPlayerStarts = UiTheme.choiceButton("");
    private final JButton computerStarts = UiTheme.choiceButton("AI goes first");
    private final JButton randomStarts = UiTheme.choiceButton("Pick randomly");
    private final JButton youngerStarts = UiTheme.choiceButton("Younger player goes first");
    private final JPanel openingTurnPanel = new JPanel();

    /**
     * Builds the setup screens.
     *
     * @param setup collects the player's answers
     * @param errorReporter shows a message when an answer cannot be accepted
     * @param completion notified once setup is finished
     */
    SetupScreens(GameSetup setup, Consumer<String> errorReporter, Completion completion) {
        this(setup, errorReporter, completion, () -> {
        });
    }

    /**
     * Builds the setup screens, with online play offered as a mode.
     *
     * @param setup collects the player's answers
     * @param errorReporter shows a message when an answer cannot be accepted
     * @param completion notified once setup is finished
     * @param onlineChosen notified when they choose to play online instead,
     *        which skips the rest of setup: names, birthdays and who starts are
     *        all the server's business in an online game
     */
    SetupScreens(GameSetup setup, Consumer<String> errorReporter, Completion completion,
            Runnable onlineChosen) {
        this.setup = setup;
        this.errorReporter = errorReporter;
        this.completion = completion;
        this.onlineChosen = onlineChosen;

        root.add(welcomeCard(), WELCOME);
        root.add(modeCard(), MODE);
        root.add(nameCard(
                "Your name",
                "Enter the name shown during this game.",
                firstNameField, this::acceptFirstName), FIRST_NAME);
        root.add(birthdayCard(
                "Your birthday", firstBirthdayField, this::acceptFirstBirthday), FIRST_BIRTHDAY);
        root.add(nameCard(
                "Second player's name",
                "Choose a different name for player two.",
                secondNameField, this::acceptSecondName), SECOND_NAME);
        root.add(birthdayCard(
                "Second player's birthday", secondBirthdayField, this::acceptSecondBirthday),
                SECOND_BIRTHDAY);
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
        JPanel panel = UiTheme.screen();
        JPanel card = UiTheme.card();
        JButton howToPlay = UiTheme.secondaryButton("How to play");
        JButton start = UiTheme.primaryButton("Start game");
        howToPlay.addActionListener(event -> HowToPlayDialog.show(root));
        start.addActionListener(event -> cards.show(root, MODE));
        card.add(UiTheme.title("Guess Who?"));
        card.add(UiTheme.gap(8));
        card.add(UiTheme.subtitle("Ask clever questions. Find the mystery character."));
        card.add(UiTheme.gap(28));
        card.add(start);
        card.add(UiTheme.gap(10));
        card.add(howToPlay);
        UiTheme.addCentered(panel, card);
        return panel;
    }

    private JPanel modeCard() {
        JPanel panel = UiTheme.screen();
        JPanel card = UiTheme.card();
        card.add(UiTheme.title("Choose a game mode"));
        card.add(UiTheme.gap(8));
        card.add(UiTheme.subtitle("Pick how you want to play."));
        card.add(UiTheme.gap(22));
        addModeChoice(card, modeButton("Play vs computer — Easy",
                () -> setup.againstComputer(ComputerDifficulty.EASY, QuestionMode.PRESET)));
        addModeChoice(card, modeButton("Play vs computer — Hard",
                () -> setup.againstComputer(ComputerDifficulty.HARD, QuestionMode.PRESET)));
        addModeChoice(card, modeButton("Play vs computer — Custom questions",
                () -> setup.againstComputer(ComputerDifficulty.HARD, QuestionMode.FREE_FORM)));
        addModeChoice(card, modeButton("Two players — Preset questions",
                () -> setup.againstPlayer(QuestionMode.PRESET)));
        addModeChoice(card, modeButton("Two players — Custom questions",
                () -> setup.againstPlayer(QuestionMode.FREE_FORM)));

        //Not a modeButton: online play does not ask for names, birthdays or who
        //starts, because the server settles all three.
        JButton online = UiTheme.choiceButton("Play online with a friend");
        online.addActionListener(event -> onlineChosen.run());
        card.add(online);
        UiTheme.addCentered(panel, card);
        return panel;
    }

    private JButton modeButton(String text, Runnable choose) {
        JButton button = UiTheme.choiceButton(text);
        button.addActionListener(event -> {
            choose.run();
            prepareOpeningTurnChoices();
            cards.show(root, FIRST_NAME);
        });
        return button;
    }

    private void addModeChoice(JPanel card, JButton choice) {
        card.add(choice);
        card.add(UiTheme.gap(8));
    }

    private JPanel nameCard(String title, String guidance, JTextField field, Runnable accept) {
        return formCard(title, guidance, field, accept);
    }

    private JPanel birthdayCard(String title, JTextField field, Runnable accept) {
        return formCard(title, "Use YYYYMMDD (for example, 20000131).", field, accept);
    }

    private JPanel formCard(String title, String guidance, JTextField field, Runnable accept) {
        JPanel panel = UiTheme.screen();
        JPanel card = UiTheme.card();
        JButton confirm = UiTheme.primaryButton("Confirm");
        confirm.addActionListener(event -> accept.run());
        UiTheme.styleInput(field);
        card.add(UiTheme.title(title));
        card.add(UiTheme.gap(8));
        card.add(UiTheme.subtitle(guidance));
        card.add(UiTheme.gap(22));
        card.add(field);
        card.add(UiTheme.gap(18));
        card.add(confirm);
        UiTheme.addCentered(panel, card);
        return panel;
    }

    private JPanel openingTurnCard() {
        JPanel panel = UiTheme.screen();
        JPanel card = UiTheme.card();
        JLabel prompt = UiTheme.title("Who goes first?");
        prompt.setHorizontalAlignment(SwingConstants.CENTER);
        openingTurnPanel.setLayout(new BoxLayout(openingTurnPanel, BoxLayout.Y_AXIS));
        openingTurnPanel.setOpaque(false);
        UiTheme.alignCenter(openingTurnPanel);
        card.add(prompt);
        card.add(UiTheme.gap(8));
        card.add(UiTheme.subtitle("Choose an option to begin the game."));
        card.add(UiTheme.gap(22));
        card.add(openingTurnPanel);
        UiTheme.addCentered(panel, card);
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
        addOpeningChoice(firstPlayerStarts);
        if (setup.isAgainstComputer()) {
            addOpeningChoice(computerStarts);
        }
        else {
            addOpeningChoice(secondPlayerStarts);
            addOpeningChoice(youngerStarts);
        }
        openingTurnPanel.add(randomStarts);
    }

    private void addOpeningChoice(JButton choice) {
        openingTurnPanel.add(choice);
        openingTurnPanel.add(UiTheme.gap(8));
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
