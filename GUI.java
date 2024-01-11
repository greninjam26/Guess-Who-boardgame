import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.border.Border;
import java.awt.Color;

public class GUI {
	// Main frame of the application
	private JFrame frame;
	private Game newGame;
	private final int width = 100;
	private final int height = 150;
	private ArrayList<String> iconStates;
	private JPanel boardPanel1;
	private JTextField usernameField1;
	private JPanel usernameAskingPanel1;
	private JButton usernameAskingButton1;
	private String username1;
	private JTextField birthdayField1;
	private JPanel birthdayAskingPanel1;
	private JButton birthdayAskingButton1;
	private int birthday1;
	private JTextField usernameField2;
	private JPanel usernameAskingPanel2;
	private JButton usernameAskingButton2;
	private String username2;
	private JTextField birthdayField2;
	private JPanel birthdayAskingPanel2;
	private JButton birthdayAskingButton2;
	private int birthday2;
	private JPanel stepPanel;
	private JComboBox<String> stepInput;
	private JButton stepChoiceButton;
	private String choice;
	private JLabel stepLabel;
	private JButton questionChoiceButton;
	private String newQuestion;
	private JComboBox<String> questionComboBox;
	private JLabel result1;
	private JButton guessButton;
	private JComboBox<String> guessComboBox;
	private String finalGuess;
	private JComboBox<String> questionAnswerComboBox;
	private JButton questionAnswerButton;
	private String questionAnswer;
	private String modeChoice;
	private String whosFirst;
	private Question AIQuestion;
	private JLabel recordStepsLabel1;
	private String recordStepsLabel1Text;
	private JLabel recordStepsLabel2;
	private String recordStepsLabel2Text;
	private JPanel endingPanel;
	private JLabel resultLabel;
	private String preSection = "";
	private JPanel inputSelectedCharacterPanel;
	private JLabel inputSelectedCharacterLabel;
	private JLabel player1SelectedCharacter;
	private JLabel AISelectedCharacter;
	private JButton inputSelectedCharacterButton;
	private JComboBox<String> charactersComboBox;
	public GUI() {
		gameGUI();
	}
	private void gameGUI() {
		frame = new JFrame("Guess Who? Game");
		frame.setPreferredSize(new Dimension(1350, 1200));// Width: 700 pixels, Height: 900 pixels
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());

		newGame = new Game();
		iconStates = new ArrayList<String>();
		JPanel controlPanel = new JPanel();
		JButton quitButton = new JButton("Quit");
		JButton restartButton = new JButton("Restart");
		controlPanel.add(quitButton);
		controlPanel.add(restartButton);
		
		JPanel welcomePanel = new JPanel();
		JLabel welcomeLabel = new JLabel("Welcme to the Guess Who? Board Game!!");
		JLabel instructionLabel = new JLabel();
		JButton instructionButton = new JButton("How To Play");
		JButton startButton = new JButton("Start The Game");
		welcomePanel.add(welcomeLabel);
		welcomePanel.add(instructionButton);
		welcomePanel.add(startButton);
		
		JPanel modeChoicePanel = new JPanel();
		JButton easyPVC = new JButton("player vs computer easy mode");
		JButton hardPVC = new JButton("player vs computer hard mode");
		JButton presetQuestionPVP = new JButton("player vs player preset questions");
		JButton freeQuestionPVP = new JButton("player vs player ask questions");
		JLabel modeChoiceLabel = new JLabel("Please choose your game mode: ");
		modeChoicePanel.add(modeChoiceLabel);
		modeChoicePanel.add(easyPVC);
		modeChoicePanel.add(hardPVC);
		modeChoicePanel.add(presetQuestionPVP);
		modeChoicePanel.add(freeQuestionPVP);
		
		usernameAskingPanel1 = new JPanel();
		JLabel usernameAskingLabel1 = new JLabel("Please enter your username (you have been warned don't make the username too long): ");
		usernameField1 = new JTextField(20);
		usernameAskingButton1 = new JButton("Comfirm");
		usernameAskingPanel1.add(usernameAskingLabel1);
		usernameAskingPanel1.add(usernameField1);
		usernameAskingPanel1.add(usernameAskingButton1);
		
		birthdayAskingPanel1 = new JPanel();
		JLabel birthdayAskingLabel1 = new JLabel("Please enter your birthday in the form of(YYYYMMDD): ");
		birthdayField1 = new JTextField(20);
		birthdayAskingButton1 = new JButton("Comfirm");
		birthdayAskingPanel1.add(birthdayAskingLabel1);
		birthdayAskingPanel1.add(birthdayField1);
		birthdayAskingPanel1.add(birthdayAskingButton1);

		usernameAskingPanel2 = new JPanel();
		JLabel usernameAskingLabel2 = new JLabel("Second player, please enter your username: ");
		usernameField2 = new JTextField(20);
		usernameAskingButton2 = new JButton("Comfirm");
		usernameAskingPanel2.add(usernameAskingLabel2);
		usernameAskingPanel2.add(usernameField2);
		usernameAskingPanel2.add(usernameAskingButton2);
		
		birthdayAskingPanel2 = new JPanel();
		JLabel birthdayAskingLabel2 = new JLabel("Please enter your birthday in the form of(YYYYMMDD): ");
		birthdayField2 = new JTextField(20);
		birthdayAskingButton2 = new JButton("Comfirm");
		birthdayAskingPanel2.add(birthdayAskingLabel2);
		birthdayAskingPanel2.add(birthdayField2);
		birthdayAskingPanel2.add(birthdayAskingButton2);
		
		JPanel characterSelectionPanel = new JPanel();
		JLabel characterSelectionLabel = new JLabel("<html>Please select a character and remember it, cause in game it will not "
				+ "be displaced. <br>Please click the ready button to start the game when you finish selecting your character. </html>");
		JButton readyButton = new JButton("Ready");
		characterSelectionPanel.add(characterSelectionLabel);
		characterSelectionPanel.add(readyButton);
		
		JPanel whosFirstChoicePanel = new JPanel();
		JLabel whosFirstChoiceLabel = new JLabel("Please choice who do you want to do first or just random: ");
		JButton AIFirstButton = new JButton("AI goes first");
		JButton player1FirstButton = new JButton();
		JButton player2FirstButton = new JButton();
		JButton randomlyChooseButton = new JButton("Randomly choose who go first");
		JButton birthdayDecideButton = new JButton("Younger person go first");
		whosFirstChoicePanel.add(whosFirstChoiceLabel);

		// Game panel with character buttons
		boardPanel1 = new JPanel(null); // 4 rows and 6 columns for 24 characters
		boardPanel1.setBounds(340, 0, 670, height*4+3*5);
		ArrayList<JButton> buttons = new ArrayList<JButton>();
		for (int i = 0; i < 24; i++) {
			ImageIcon characterIcon = new ImageIcon(getClass().getResource("" + i + ".jpg"));
			Image image = characterIcon.getImage();
			Image newimg = image.getScaledInstance(width, height,  java.awt.Image.SCALE_SMOOTH);
			characterIcon = new ImageIcon(newimg);
			JButton characterButton = new JButton(characterIcon);//button.setIcon(newIcon); change the icon
			int x = width*(i%6) + 10*(i%6+1);
			int y = height*(i/6) + 5*(i/6+1);
			characterButton.setBounds(x, y, width, height);
			buttons.add(characterButton);
			iconStates.add("front");
			// Add action listeners to character buttons here if needed
			boardPanel1.add(characterButton);
		}
		
        stepPanel = new JPanel(null);
		stepLabel = new JLabel("Please make your choice: 1. ask question. 2. guess the character");
		stepLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center the label text
		stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height
		String[] stepChoice = {"1", "2"};
		stepInput = new JComboBox<>(stepChoice);
		stepInput.setBounds(640, 675, 75, 30); // x, y, width, height
		stepChoiceButton = new JButton("Comfirm");
		stepChoiceButton.setBounds(715, 675, 100, 30); // x, y, width, height
		questionChoiceButton = new JButton("Comfirm");
		questionChoiceButton.setBounds(790, 675, 100, 30); // x, y, width, height
		guessButton = new JButton("Guess");
		guessButton.setBounds(715, 675, 100, 30); // x, y, width, height
		result1 = new JLabel("");
		result1.setBounds(0, 965, 700, 30); // x, y, width, height
		result1.setHorizontalAlignment(SwingConstants.CENTER); // Center the label text
		String[] questionChoices = {"yes", "no"};
		questionAnswerComboBox = new JComboBox<String>(questionChoices);
		questionAnswerComboBox.setBounds(640, 675, 75, 30); // x, y, width, height
		questionAnswerButton = new JButton("Confirm");
		questionAnswerButton.setBounds(715, 675, 100, 30); // x, y, width, height
		JButton nextTurnButton = new JButton("Next Turn");
		nextTurnButton.setBounds(890, 705, 100, 30); // x, y, width, height
		stepPanel.add(stepLabel);
		stepPanel.add(result1);
		stepPanel.add(nextTurnButton);
		nextTurnButton.setVisible(false);
		
		// Create a line border with the specified color and width
		Border border = BorderFactory.createLineBorder(Color.BLACK, 2);
		
		recordStepsLabel1Text = "<html>";
		JPanel recordStepsPanel1 = new JPanel(null);
		recordStepsPanel1.setBounds(1010, 0, 340, 700);
		JLabel recordStepsHeadLabel1 = new JLabel();
		recordStepsLabel1 = new JLabel(recordStepsLabel1Text);
		recordStepsPanel1.add(recordStepsHeadLabel1);
		recordStepsPanel1.add(recordStepsLabel1);
		// Set the border for the JPanel
		recordStepsPanel1.setBorder(border);

		recordStepsLabel2Text = "<html>";
		JPanel recordStepsPanel2 = new JPanel(null);
		recordStepsPanel2.setBounds(0, 0, 340, 700);
		JLabel recordStepsHeadLabel2 = new JLabel();
		recordStepsLabel2 = new JLabel(recordStepsLabel2Text);
		recordStepsPanel2.add(recordStepsHeadLabel2);
		recordStepsPanel2.add(recordStepsLabel2);
		// Set the border for the JPanel
		recordStepsPanel2.setBorder(border);
		
		endingPanel = new JPanel(new FlowLayout());
		resultLabel = new JLabel("");
		resultLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center the label text
		endingPanel.add(resultLabel);
		JLabel validateLabel = new JLabel("");
		JButton AIWrongButton = new JButton("AI is wrong!!!");
		
		inputSelectedCharacterPanel = new JPanel();
		inputSelectedCharacterLabel = new JLabel("<html>The Game is Over!! <br>Please selected the Character you selected for the game: <html>");
		inputSelectedCharacterButton = new JButton("Confirm");
		inputSelectedCharacterPanel.add(inputSelectedCharacterLabel);
		
		// Add start panel to the frame
		frame.add(welcomePanel, BorderLayout.CENTER);
		frame.add(controlPanel, BorderLayout.SOUTH);
		// Show the frame
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		buttons.get(0).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(0), iconStates.get(0), 0);
			}
		});
		buttons.get(1).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(1), iconStates.get(1), 1);
			}
		});
		buttons.get(2).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(2), iconStates.get(2), 2);
			}
		});
		buttons.get(3).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(3), iconStates.get(3), 3);
			}
		});
		buttons.get(4).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(4), iconStates.get(4), 4);
			}
		});
		buttons.get(5).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(5), iconStates.get(5), 5);
			}
		});
		buttons.get(6).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(6), iconStates.get(6), 6);
			}
		});
		buttons.get(7).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(7), iconStates.get(7), 7);
			}
		});
		buttons.get(8).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(8), iconStates.get(8), 8);
			}
		});
		buttons.get(9).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(9), iconStates.get(9), 9);
			}
		});
		buttons.get(10).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(10), iconStates.get(10), 10);
			}
		});
		buttons.get(11).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(11), iconStates.get(11), 11);
			}
		});
		buttons.get(12).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(12), iconStates.get(12), 12);
			}
		});
		buttons.get(13).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(13), iconStates.get(13), 13);
			}
		});
		buttons.get(14).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(14), iconStates.get(14), 14);
			}
		});
		buttons.get(15).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(15), iconStates.get(15), 15);
			}
		});
		buttons.get(16).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(16), iconStates.get(16), 16);
			}
		});
		buttons.get(17).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(17), iconStates.get(17), 17);
			}
		});
		buttons.get(18).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(18), iconStates.get(18), 18);
			}
		});
		buttons.get(19).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(19), iconStates.get(19), 19);
			}
		});
		buttons.get(20).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(20), iconStates.get(20), 20);
			}
		});
		buttons.get(21).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(21), iconStates.get(21), 21);
			}
		});
		buttons.get(22).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(22), iconStates.get(22), 22);
			}
		});
		buttons.get(23).addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newButtonIcon(buttons.get(23), iconStates.get(23), 23);
			}
		});
		quitButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.dispose();
			}
		});
		restartButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				stepPanel.getRootPane().removeAll();
				frame.getContentPane().removeAll();
			}
		});
		instructionButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				welcomePanel.add(instructionLabel);
			}
		});
		startButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(welcomePanel);
				frame.add(modeChoicePanel, BorderLayout.CENTER);
				frame.revalidate();
				frame.repaint();
			}
		});
		usernameAskingButton1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				username1 = usernameField1.getText();
				frame.remove(usernameAskingPanel1);
				player1FirstButton.setText(username1 + " goes first");
				frame.add(whosFirstChoicePanel);
				frame.revalidate();
				frame.repaint();
			}
		});
		easyPVC.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				modeChoice = "player vs computer easy mode";
				frame.remove(modeChoicePanel);
				// Get the selected item
				frame.add(usernameAskingPanel1, BorderLayout.CENTER);
				whosFirstChoicePanel.add(player1FirstButton);
				whosFirstChoicePanel.add(AIFirstButton);
				whosFirstChoicePanel.add(randomlyChooseButton);
				frame.revalidate();
				frame.repaint();
			}
		});
		hardPVC.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				modeChoice = "player vs computer hard mode";
				frame.remove(modeChoicePanel);
				// Get the selected item
				frame.add(usernameAskingPanel1, BorderLayout.CENTER);
				whosFirstChoicePanel.add(player1FirstButton);
				whosFirstChoicePanel.add(AIFirstButton);
				whosFirstChoicePanel.add(randomlyChooseButton);
				frame.revalidate();
				frame.repaint();
			}
		});
		presetQuestionPVP.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				modeChoice = "player vs player preset questions";
				frame.remove(modeChoicePanel);
				// Get the selected item
				frame.add(usernameAskingPanel1, BorderLayout.CENTER);
				whosFirstChoicePanel.add(player1FirstButton);
				whosFirstChoicePanel.add(player2FirstButton);
				whosFirstChoicePanel.add(birthdayDecideButton);
				whosFirstChoicePanel.add(randomlyChooseButton);
				frame.revalidate();
				frame.repaint();
			}
		});
		freeQuestionPVP.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				modeChoice = "player vs player ask questions";
				frame.remove(modeChoicePanel);
				// Get the selected item
				frame.add(usernameAskingPanel1, BorderLayout.CENTER);
				whosFirstChoicePanel.add(player1FirstButton);
				whosFirstChoicePanel.add(player2FirstButton);
				whosFirstChoicePanel.add(birthdayDecideButton);
				whosFirstChoicePanel.add(randomlyChooseButton);
				frame.revalidate();
				frame.repaint();
			}
		});
		AIFirstButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(whosFirstChoicePanel);
				try {
					whosFirst = newGame.playerVsComputerAIFirst("AI " + modeChoice.substring(19, 23), username1);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				frame.add(characterSelectionPanel);
				frame.revalidate();
				frame.repaint();
			}
		});
		player1FirstButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(whosFirstChoicePanel);
				if (modeChoice.substring(0, 16).equals("player vs player")) {
					frame.add(birthdayAskingPanel1, BorderLayout.CENTER);
					frame.revalidate();
					frame.repaint();
				}
				else {
					try {
						whosFirst = newGame.playerVsComputerPlayerFirst("AI " + modeChoice.substring(19, 23), username1);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					frame.add(characterSelectionPanel);
					frame.revalidate();
					frame.repaint();
				}
			}
		});
		player2FirstButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(whosFirstChoicePanel);
				 
			}
		});
		randomlyChooseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(whosFirstChoicePanel);
				try {
					whosFirst = newGame.playerVsComputerRandom("AI " + modeChoice.substring(19, 23), username1);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				result1.setText(whosFirst);
				System.out.println(whosFirst);
				frame.add(characterSelectionPanel);
				frame.revalidate();
				frame.repaint();
			}
		});
		readyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(characterSelectionPanel);
				frame.add(boardPanel1);
				frame.add(stepPanel);
				frame.add(recordStepsPanel1, BorderLayout.EAST);
				frame.add(recordStepsPanel2, BorderLayout.WEST);
				frame.revalidate();
				frame.repaint();
				oneTurn();
			}
		});
		stepChoiceButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				choice = (String) stepInput.getSelectedItem();
				stepPanel.remove(stepInput);
				stepPanel.remove(stepChoiceButton);
				if (choice.equals("1")) {
					stepLabel.setText("Please choice the question you want to ask: ");
					stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height
					ArrayList<Question> questionsLeft = newGame.getUser1().getUnAskedQuestions();
					String[] questions = new String[questionsLeft.size()];
					for (int i = 0; i < questionsLeft.size(); i++) {
						questions[i] = questionsLeft.get(i).getQuestion();
					}
					questionComboBox = new JComboBox<String>(questions);
					questionComboBox.setBounds(490, 675, 300, 30); // x, y, width, height
					stepPanel.add(questionComboBox);
					stepPanel.add(questionChoiceButton);
					frame.revalidate();
					frame.repaint();
				}
				else {
					stepLabel.setText(newGame.getUser1().getUsername() + ", please enter your guess: ");
					stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height
					ArrayList<Character> validCharacters = newGame.getUser1().getGameBoard().getCharacters();
					String[] characters = new String[validCharacters.size()];
					for (int i = 0; i < validCharacters.size(); i++) {
						characters[i] = validCharacters.get(i).getName();
					}
					guessComboBox = new JComboBox<String>(characters);
					guessComboBox.setBounds(565, 675, 150, 30); // x, y, width, height
					stepPanel.add(guessComboBox);
					stepPanel.add(guessButton);
					frame.revalidate();
					frame.repaint();
				}
			}
		});
		questionChoiceButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newQuestion = (String) questionComboBox.getSelectedItem();
				String AIAnswer = newGame.AskAI(newQuestion);
				result1.setText("AI: " + AIAnswer);
				result1.setVisible(true);
				recordStepsLabel1Text += newQuestion + " : " + AIAnswer + "<br>";
				recordStepsLabel1.setText(recordStepsLabel1Text);
				newGame.getUser1().setIsTurn(false);
				newGame.getAI().setIsTurn(true);
				nextTurnButton.setVisible(true);
				//have space of waiting period
				//skip next turn button
				preSection = "questionChoice";
			}
		});
		nextTurnButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (preSection.equals("questionChoice")) {
					stepPanel.remove(questionComboBox);
					stepPanel.remove(questionChoiceButton);
				}
				nextTurnButton.setVisible(false);
				result1.setVisible(false);
				oneTurn();
			}
		});
		questionAnswerButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				questionAnswer = (String) questionAnswerComboBox.getSelectedItem();
				newGame.getAI().askQuestion(AIQuestion.getQuestion(), questionAnswer);
				recordStepsLabel2Text += questionAnswer + "<br>";
				recordStepsLabel2.setText(recordStepsLabel2Text);
				if (questionAnswer.equals("yes")) {
					newGame.getUser1().setIsTurn(true);
				}
				else {
					newGame.getUser1().setIsTurn(false);
				}
				stepPanel.remove(questionAnswerButton);
				stepPanel.remove(questionAnswerComboBox);
				newGame.getAI().addQuestionAnswers(true);
				newGame.getAI().setIsTurn(false);
				nextTurnButton.setVisible(true);
			}
		});
		guessButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				finalGuess = (String) guessComboBox.getSelectedItem();
		        resultLabel.setText(newGame.guessAI(finalGuess));
		        frame.remove(boardPanel1);
		        frame.remove(stepPanel);
				ArrayList<Character> validCharacters = newGame.getUser1().getGameBoard().getCharacters();
				String[] characters = new String[validCharacters.size()];
				for (int i = 0; i < validCharacters.size(); i++) {
					characters[i] = validCharacters.get(i).getName();
				}
				charactersComboBox = new JComboBox<String>(characters);
				inputSelectedCharacterPanel.add(charactersComboBox);
				inputSelectedCharacterPanel.add(inputSelectedCharacterButton);
		        frame.add(inputSelectedCharacterPanel);
				frame.revalidate();
				frame.repaint();
			}
		});
		inputSelectedCharacterButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String userCharacterName = (String) charactersComboBox.getSelectedItem();
				Character userCharacter = newGame.getUser1().findCharacter(userCharacterName);
				newGame.getUser1().setSelectedCharacter(userCharacter);
		        AISelectedCharacter = getAICharacterImage(newGame.getAI());
		        player1SelectedCharacter = getCharacterImage(newGame.getUser1());
				frame.remove(inputSelectedCharacterPanel);
		        endingPanel.add(AISelectedCharacter);
		        endingPanel.add(resultLabel);
		        endingPanel.add(player1SelectedCharacter);
				newGame.setState("finished");
				String validateResult = "";
				if (newGame.checkUserAnswers()) {
					validateResult = "<html>Your answer to the questions is all correct!!! <br>Thank you for doing to correctly!! :)";
				}
				else {
					ArrayList<Question> wrongAnsweredQuestion = newGame.getAI().getQuestionsAnsweredWrong();
					ArrayList<Boolean> wrongAnswers = newGame.getAI().getAnswerQuestionsAnsweredWrong();
					validateResult = "<html>you answered " + wrongAnsweredQuestion.size() + " questions wrong!!! :(<br>";
					for (int i = 0; i < wrongAnsweredQuestion.size(); i ++) {
						if (wrongAnswers.get(i)) {
							validateResult += wrongAnsweredQuestion.get(i).getQuestion() + " : yes<br>";
						}
						else {
							validateResult += wrongAnsweredQuestion.get(i).getQuestion() + " : no<br>";
						}
					}
				}
				validateResult += "<html>";
				validateLabel.setText(validateResult);
				endingPanel.add(validateLabel);
		        frame.add(endingPanel, BorderLayout.CENTER);
				frame.revalidate();
				frame.repaint();
			}
		});
		birthdayAskingButton1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				birthday1 = Integer.parseInt(birthdayField1.getText());
				frame.remove(birthdayAskingPanel1);
				frame.add(usernameAskingPanel2, BorderLayout.CENTER);
				frame.revalidate();
				frame.repaint();
			}
		});
		usernameAskingButton2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				username2 = usernameField2.getText();
				frame.remove(usernameAskingPanel2);
				frame.add(birthdayAskingPanel2, BorderLayout.CENTER);
				frame.revalidate();
				frame.repaint();
			}
		});
		birthdayAskingButton2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				birthday2 = Integer.parseInt(birthdayField2.getText());
				frame.remove(birthdayAskingPanel2);
			}
		});
	}
	private void oneTurn() {
		if (newGame.getUser1().getIsTurn()) {//player turn
			stepLabel.setText("Please make your choice: 1. ask question. 2. guess the character");
			stepPanel.add(stepInput);
			stepPanel.add(stepChoiceButton);
			frame.revalidate();
			frame.repaint();
		}
		else {//AI turn
			if (newGame.getAI().onlyOne()) {
				String question = "Is " + newGame.getAI().onlyOneLeft() + " the character? ";
				int result = JOptionPane.showConfirmDialog(null, question, "Confirmation", JOptionPane.YES_NO_OPTION);
		        if (result == JOptionPane.YES_OPTION) {// User chose YES
		        	String ans = newGame.getAI().playGuess(username1, false);
		        	resultLabel.setText(ans);
		        	JOptionPane.showMessageDialog(null, ans, "Message", JOptionPane.INFORMATION_MESSAGE);
		        }
		        else {// User chose NO
		        	String ans = newGame.getAI().playGuess(username1, true);
		        	resultLabel.setText(ans);
		        	JOptionPane.showMessageDialog(null, ans, "Message", JOptionPane.INFORMATION_MESSAGE);
		        }
		        newGame.setState("finished");
		        frame.remove(boardPanel1);
		        frame.remove(stepPanel);
				ArrayList<Character> validCharacters = newGame.getUser1().getGameBoard().getCharacters();
				String[] characters = new String[validCharacters.size()];
				for (int i = 0; i < validCharacters.size(); i++) {
					characters[i] = validCharacters.get(i).getName();
				}
				charactersComboBox = new JComboBox<String>(characters);
				inputSelectedCharacterPanel.add(charactersComboBox);
				inputSelectedCharacterPanel.add(inputSelectedCharacterButton);
		        frame.add(inputSelectedCharacterPanel);
				frame.revalidate();
				frame.repaint();
			}
			else {
				AIQuestion = newGame.getAI().playQuestion();
				String choosenQuestion = AIQuestion.getQuestion();
				System.out.println("????????????????????//////" + choosenQuestion);
				stepLabel.setText(choosenQuestion);
				stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height
				stepLabel.setVisible(true);
				stepPanel.add(questionAnswerButton);
				stepPanel.add(questionAnswerComboBox);
				recordStepsLabel2Text += choosenQuestion + " : ";
				recordStepsLabel2.setText(recordStepsLabel2Text);
				frame.revalidate();
				frame.repaint();
			}
		}
	}
	private JLabel getCharacterImage(User player) {
        int characterIndex = player.getSelectedCharacter().getCharacterIndex();
        ImageIcon characterIcon = new ImageIcon(getClass().getResource("" + characterIndex + ".jpg"));
		Image image = characterIcon.getImage();
		Image newimg = image.getScaledInstance(width, height,  java.awt.Image.SCALE_SMOOTH);
		characterIcon = new ImageIcon(newimg);
		JLabel characterLabel = new JLabel(characterIcon);
		return characterLabel;
	}
	private JLabel getAICharacterImage(ComputerPlayer AIPlayer) {
        int characterIndex = AIPlayer.getSelectedCharacter().getCharacterIndex();
        ImageIcon characterIcon = new ImageIcon(getClass().getResource("" + characterIndex + ".jpg"));
		Image image = characterIcon.getImage();
		Image newimg = image.getScaledInstance(width, height,  java.awt.Image.SCALE_SMOOTH);
		characterIcon = new ImageIcon(newimg);
		JLabel characterLabel = new JLabel(characterIcon);
		return characterLabel;
	}
	private void newButtonIcon(JButton button, String iconState, int index) {
		if (iconState.equals("front")) {
			ImageIcon characterIcon = new ImageIcon(getClass().getResource("characterGone.jpg"));
			Image image = characterIcon.getImage();
			Image newimg = image.getScaledInstance(width, height,  java.awt.Image.SCALE_SMOOTH);
			characterIcon = new ImageIcon(newimg);
			button.setIcon(characterIcon);//button.setIcon(newIcon); change the icon
			iconStates.set(index, "back");
		}
		else {
			ImageIcon characterIcon = new ImageIcon(getClass().getResource("" + index + ".jpg"));
			Image image = characterIcon.getImage();
			Image newimg = image.getScaledInstance(width, height,  java.awt.Image.SCALE_SMOOTH);
			characterIcon = new ImageIcon(newimg);
			button.setIcon(characterIcon);//button.setIcon(newIcon); change the icon
			iconStates.set(index, "front");
		}
	}
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new GUI();
			}
		});
	}
}