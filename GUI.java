/*Author: Gavin Liu, Bagavan Marakathalingasivam, Andreas Li
 * Date: Jan 8 2024
 * Description: this class contains all the basic front end code that have all the buttons and panels working 
 * but the styling should be improved on for it to look good. 
 * */
import java.io.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.sound.sampled.*;
import javax.swing.border.Border;

public class GUI {
	// Main frame of the application
	private JFrame frame;
	//new game class for the game
	private Game newGame;
	//new StoreResult object use to store the game data and result
	private StoreResult store;
	//the music
	private static Clip music;
	//the list of characters image
	private ArrayList<ImageIcon> characterImages = new ArrayList<ImageIcon>();
	//image for the characters that were elimated
	private ImageIcon back;
	//the size of the image
	private final int width = 100;
	private final int height = 150;
	//the veriables needed for the GUI to work
	private ArrayList<String> iconStates1;//the state of the icon for the first user's board
	private JPanel boardPanel1;//first user's board
	private ArrayList<String> iconStates2;//the state of the icon for the second user's or the AI's board
	private JPanel boardPanel2;//second user's or the AI's board
	private JPanel guessBoardPanel;
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
	private JTextField questionTextField;
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
	private String preSection;
	private JPanel inputSelectedCharacterPanel1;
	private JLabel inputSelectedCharacterLabel1;
	private JButton inputSelectedCharacterButton1;
	private JComboBox<String> charactersComboBox1;
	private JPanel inputSelectedCharacterPanel2;
	private JLabel inputSelectedCharacterLabel2;
	private JButton inputSelectedCharacterButton2;
	private JComboBox<String> charactersComboBox2;
	private JLabel player1SelectedCharacter;
	private JLabel player2SelectedCharacter;
	private JLabel AISelectedCharacter;
	private JPanel whosFirstChoicePanel;
	private JButton player1FirstButton;
	private JButton player2FirstButton;
	private JButton randomlyChooseButton;
	private JButton birthdayDecideButton;
	private JPanel modeChoicePanel;
	private JButton AIFirstButton;
	private JButton askButton;
	private String curPlayer;
	private JButton guess;
	private JButton next;
	public GUI() {
		readAllImages();
		gameGUI();
	}
	private void gameGUI() {
		frame = new JFrame("Guess Who? Game");//name of the frame
		frame.setPreferredSize(new Dimension(1350, 1200));// Width: 700 pixels, Height: 900 pixels
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());
		//inialization of some of the veriables
		newGame = new Game();
		preSection = "";
		iconStates1 = new ArrayList<String>();
		iconStates2 = new ArrayList<String>();
		curPlayer = "";
		JPanel controlPanel = new JPanel();
		JButton quitButton = new JButton("Quit");
		JButton restartButton = new JButton("Restart");
		controlPanel.add(quitButton);
		controlPanel.add(restartButton);
		//welcomePanel setup
		JPanel welcomePanel = new JPanel();
		JLabel welcomeLabel = new JLabel("Welcome to the Guess Who? Board Game!!");
		String instructions = "<html><body style='font-family:Arial; font-size:14;'>" +
	               "<br><br><h1>Welcome to Guess Who Online!</h1>" +
	               "The game starts with two players, each drawing a unique character card from a deck of 24 characters. " +
	               "Each player has a game board containing each of the 24 characters. <br>Players try to determine their opponent's " +
	               "hidden character by asking a series of yes or no questions based on their character's attributes. <br>" +
	               "Characters are eliminated using the process of elimination; they use the gameboard to record possible suspects " +
	               "by flipping down the character cards that don't match. <br>The first player to correctly guess their opponent's " +
	               "character wins the game, but if the players guess incorrectly, they lose.<br>" +
	               "<br>Guess Who Online has two game modes: <strong>Player-versus-player</strong> and " +
	               "<strong>Player-versus-computer</strong>. The player-versus-computer game mode has three difficulties: easy, hard. <br>" +
	               "The player-versus-player mode has two game options: predetermined questions and free questions. <br>" +
	               "After each round, the scores will be validated and added to the leaderboard." +
	               "In the game, you can ask a yes or no question about your opponent's characters using the " +
	               "<strong>\"Ask Question\"</strong> button. <br>When you wish to guess who your opponent character is, click the " +
	               "<strong>\"Guess\"</strong> button, then select a character on the board. Characters can be flipped down by clicking <br>" +
	               "on their icons on the board.<br>" +
	               "<br>We hope you enjoy the game!<br>" +
	               "</body></html>";
		JLabel instructionLabel = new JLabel(instructions);
		JButton instructionButton = new JButton("How To Play");
		JButton startButton = new JButton("Start The Game");
		welcomePanel.add(welcomeLabel);
		welcomePanel.add(instructionButton);
		welcomePanel.add(startButton);
		//the panel is use to let the player to choose the game mode
		modeChoicePanel = new JPanel();
		//four buttons for the four different game modes
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
		//the panel is used to ask the first player to enter their username
		usernameAskingPanel1 = new JPanel();
		JLabel usernameAskingLabel1 = new JLabel("Please enter your username (you have been warned don't make the username too long): ");
		usernameField1 = new JTextField(20);
		usernameAskingButton1 = new JButton("Comfirm");
		usernameAskingPanel1.add(usernameAskingLabel1);
		usernameAskingPanel1.add(usernameField1);
		usernameAskingPanel1.add(usernameAskingButton1);
		//the panel is used to ask the first player to enter their birthday
		birthdayAskingPanel1 = new JPanel();
		JLabel birthdayAskingLabel1 = new JLabel("Please enter your birthday in the form of(YYYYMMDD): ");
		birthdayField1 = new JTextField(20);
		birthdayAskingButton1 = new JButton("Comfirm");
		birthdayAskingPanel1.add(birthdayAskingLabel1);
		birthdayAskingPanel1.add(birthdayField1);
		birthdayAskingPanel1.add(birthdayAskingButton1);
		//the panel is used to ask the second player to enter their username
		usernameAskingPanel2 = new JPanel();
		JLabel usernameAskingLabel2 = new JLabel("Second player, please enter your username(please don't enter the same username as the first player): ");
		usernameField2 = new JTextField(20);
		usernameAskingButton2 = new JButton("Comfirm");
		usernameAskingPanel2.add(usernameAskingLabel2);
		usernameAskingPanel2.add(usernameField2);
		usernameAskingPanel2.add(usernameAskingButton2);
		//the panel is used to ask the second player to enter their birthday
		birthdayAskingPanel2 = new JPanel();
		JLabel birthdayAskingLabel2 = new JLabel("Please enter your birthday in the form of(YYYYMMDD): ");
		birthdayField2 = new JTextField(20);
		birthdayAskingButton2 = new JButton("Comfirm");
		birthdayAskingPanel2.add(birthdayAskingLabel2);
		birthdayAskingPanel2.add(birthdayField2);
		birthdayAskingPanel2.add(birthdayAskingButton2);
		
		JPanel characterSelectionPanel = new JPanel();
		JLabel characterSelectionLabel = new JLabel("<html>Please select a character and remember it, cause in game it will not "
				+ "be displaced. <br>Please click the ready button to start the game when you finish selecting your character. <html>");
		JButton readyButton = new JButton("Ready");
		characterSelectionPanel.add(characterSelectionLabel);
		characterSelectionPanel.add(readyButton);
		
		whosFirstChoicePanel = new JPanel();
		JLabel whosFirstChoiceLabel = new JLabel("Please choice who do you want to do first or just random: ");
		AIFirstButton = new JButton("AI goes first");
		player1FirstButton = new JButton();
		player2FirstButton = new JButton();
		randomlyChooseButton = new JButton("Randomly choose who go first");
		birthdayDecideButton = new JButton("Younger person go first");
		whosFirstChoicePanel.add(whosFirstChoiceLabel);
		
		// Game panel with character buttons
		//board1 is the game board for the first player
		//board2 is the game board for the second player
		//the guess board is used for the players to enter their guess in pvp mode
		boardPanel1 = new JPanel(null); // 4 rows and 6 columns for 24 characters
		boardPanel1.setBounds(340, 35, 670, height*4+3*5); // x, y, width, height
		ArrayList<JButton> buttons1 = new ArrayList<JButton>();
		boardPanel2 = new JPanel(null); // 4 rows and 6 columns for 24 characters
		boardPanel2.setBounds(340, 35, 670, height*4+3*5); // x, y, width, height
		ArrayList<JButton> buttons2 = new ArrayList<JButton>();
		guessBoardPanel = new JPanel(null); // 4 rows and 6 columns for 24 characters
		guessBoardPanel.setBounds(340, 35, 670, height*4+3*5); // x, y, width, height
		ArrayList<JButton> buttons3 = new ArrayList<JButton>();
		for (int i = 0; i < 24; i++) {
			ImageIcon characterIcon = characterImages.get(i);
			JButton characterButton1 = new JButton(characterIcon);
			JButton characterButton2 = new JButton(characterIcon);
			JButton characterButton3 = new JButton(characterIcon);
			int x = width*(i%6) + 10*(i%6+1);
			int y = height*(i/6) + 5*(i/6+1);
			characterButton1.setBounds(x, y, width, height);
			characterButton2.setBounds(x, y, width, height);
			characterButton3.setBounds(x, y, width, height);
			buttons1.add(characterButton1);
			buttons2.add(characterButton2);
			buttons3.add(characterButton3);
			iconStates1.add("front");
			iconStates2.add("front");
			// Add action listeners to character buttons here if needed
			boardPanel1.add(characterButton1);
			boardPanel2.add(characterButton2);
			guessBoardPanel.add(characterButton3);
		}
		//stepPanel is used in each turn the user ask questions, 
		//enter guess and choice what is their next step, wether to ask a question or make a guess
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
		result1.setBounds(0, 705, 1350, 30); // x, y, width, height
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
		Border border1 = BorderFactory.createLineBorder(Color.BLACK, 2);
		Border border2 = BorderFactory.createLineBorder(Color.BLACK, 2);
		//records all the questions asked by the first player, and the answer they got in return
		recordStepsLabel1Text = "<html>";
		JPanel recordStepsPanel1 = new JPanel();
		recordStepsLabel1 = new JLabel(recordStepsLabel1Text);
		recordStepsPanel1.add(recordStepsLabel1);
		// Set the border for the JPanel
		recordStepsPanel1.setBorder(border1);
		//records all the questions asked by the opponent of the first player, and the answer the first player inputed. 
		recordStepsLabel2Text = "<html>";
		JPanel recordStepsPanel2 = new JPanel();
		recordStepsLabel2 = new JLabel(recordStepsLabel2Text);
		recordStepsPanel2.add(recordStepsLabel2);
		// Set the border for the JPanel
		recordStepsPanel2.setBorder(border2);
		//this panel is used to display the ending massages
		endingPanel = new JPanel(new FlowLayout());
		resultLabel = new JLabel("");
		resultLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center the label text
		endingPanel.add(resultLabel);
		JLabel validateLabel = new JLabel("");
		//this panel is used to leftthe first player to enter their selected character
		inputSelectedCharacterPanel1 = new JPanel();
		inputSelectedCharacterLabel1 = new JLabel("<html>The Game is Over!! <br>Please selected the Character you selected for the game: <html>");
		inputSelectedCharacterButton1 = new JButton("Confirm");
		inputSelectedCharacterPanel1.add(inputSelectedCharacterLabel1);
		//this the for the second player to enter the selected character
		inputSelectedCharacterPanel2 = new JPanel();
		inputSelectedCharacterLabel2 = new JLabel("<html>Second Player <br>Please selected the Character you selected for the game: <html>");
		inputSelectedCharacterButton2 = new JButton("Confirm");
		inputSelectedCharacterPanel2.add(inputSelectedCharacterLabel2);
		//ask question button for PVP
		askButton = new JButton("ask question");
		askButton.setBounds(790, 675, 150, 30); // x, y, width, height
		//next turn button for PVP
		next = new JButton("next");
		next.setBounds(940, 675, 75, 30); // x, y, width, height
		//guess button for PVP
		guess = new JButton("guess");
		guess.setBounds(1015, 675, 100, 30); // x, y, width, height
		
		// Add start panel to the frame
		frame.add(welcomePanel, BorderLayout.CENTER);
		frame.add(controlPanel, BorderLayout.NORTH);
		// Show the frame
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		//use a for loop for all the action listener for all the character buttons in the board for the first player
		for (int j = 0; j < 24; j++) {
			final int i = j;
			buttons1.get(i).addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					newButtonIcon(buttons1.get(i), iconStates1, i);//call the newButtonIcon method to change the icon of the button
				}
			});
		}
		//use a for loop for all the action listener for all the character buttons in the board for the second player
		for (int j = 0; j < 24; j++) {
			final int i = j;
			buttons2.get(i).addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					newButtonIcon(buttons2.get(i), iconStates2, i);//call the newButtonIcon method to change the icon of the button
				}
			});
		}
		//use a for loop for all the action listener for all the character buttons in the board that is used to guess
		for (int j = 0; j < 24; j++) {
			final int i = j;
			buttons3.get(i).addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					frame.remove(guessBoardPanel);
					if (newGame.getUser1().getIsTurn()) {//if it user1's turn
						guessPVP(newGame.getUser1(), newGame.getUser2(), i);//user1 do the guess
					}
					else {//when it is user2's turn
						guessPVP(newGame.getUser2(), newGame.getUser1(), i);//user2 do the guess
					}
				}
			});
		}
		//this action listener is used to dispose the frame and stop the music and entire program
		quitButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.dispose();//close the frame
				music.close();//stop the music
			}
		});
		//this action listener is used to dispose the frame and stop the music and restart entire program
		restartButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.dispose();//close the frame
				gameGUI();//make a new one
			}
		});
		//this action listener will add in the instruction to the game when the button is clicked
		instructionButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				welcomePanel.add(instructionLabel);
				refreshFrame();
			}
		});
		//this action Listener will start that game
		startButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(welcomePanel);
				frame.add(modeChoicePanel, BorderLayout.CENTER);
				refreshFrame();
			}
		});
		//action listener for the when user 1 finish entering their username
		usernameAskingButton1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				username1 = usernameField1.getText();//read the username from the JTextField
				frame.remove(usernameAskingPanel1);
				newGame.setState(modeChoice);//set the state
				player1FirstButton.setText(username1 + " goes first");
				recordStepsLabel1Text += username1 + ": <br>";
				if (modeChoice.startsWith("player vs player")) {//if the user choose to do player vs player
					frame.add(birthdayAskingPanel1);
				}
				else {//when it is player vs computer
					recordStepsLabel2Text += "AI: <br>";
					frame.add(whosFirstChoicePanel);
				}
				refreshFrame();
			}
		});
		//action listener for when the first player finish inputing their birthday, the second player start to input their username
		birthdayAskingButton1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				birthday1 = Integer.parseInt(birthdayField1.getText());
				frame.remove(birthdayAskingPanel1);
				frame.add(usernameAskingPanel2, BorderLayout.CENTER);
				refreshFrame();
			}
		});
		//action listener for when the second player finish inputing their username, and start input their birthday
		usernameAskingButton2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				username2 = usernameField2.getText();
				player2FirstButton.setText(username2 + " goes first");
				recordStepsLabel2Text += username2 + ": <br>";
				frame.remove(usernameAskingPanel2);
				frame.add(birthdayAskingPanel2, BorderLayout.CENTER);
				refreshFrame();
			}
		});
		//action listener for when the second player finish inputing their birthday, and start choosing who goes first
		birthdayAskingButton2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				birthday2 = Integer.parseInt(birthdayField2.getText());
				frame.remove(birthdayAskingPanel2);
				frame.add(whosFirstChoicePanel);
				refreshFrame();
			}
		});
		//choosing the gamemode buttons actionListener
		easyPVC.addActionListener(new ActionListener() {//play against easy AI
			@Override
			public void actionPerformed(ActionEvent e) {
				modeChoice = "player vs computer easy mode";
				askUsernamePVC();
			}
		});
		hardPVC.addActionListener(new ActionListener() {//play against hard AI
			@Override
			public void actionPerformed(ActionEvent e) {
				modeChoice = "player vs computer hard mode";
				askUsernamePVC();
			}
		});
		presetQuestionPVP.addActionListener(new ActionListener() {//play against another player with predefined questions
			@Override
			public void actionPerformed(ActionEvent e) {
				modeChoice = "player vs player preset questions";
				askUsernamePVP();
			}
		});
		freeQuestionPVP.addActionListener(new ActionListener() {//play against another player with whatever questions you enter
			@Override
			public void actionPerformed(ActionEvent e) {
				modeChoice = "player vs player ask questions";
				askUsernamePVP();
			}
		});
		//action listener for when user choose to let AI go first
		AIFirstButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(whosFirstChoicePanel);
				try {
					newGame.playerVsComputerAIFirst("AI " + modeChoice.substring(19, 23), username1);//call the method and start the game with AI first
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				frame.add(characterSelectionPanel);//add in the character selection for the user to get ready to start the game
				refreshFrame();
			}
		});
		//action listener for when user choose, that they should go first
		player1FirstButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(whosFirstChoicePanel);
				if (modeChoice.startsWith("player vs player")) {//when it is pvp mode
					try {
						newGame.playerVsPlayer1First(username1, birthday1, username2, birthday2);//call the method and start the game with player 1 first
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					frame.add(characterSelectionPanel);//add in the character selection for the user to get ready to start the game
					refreshFrame();
				}
				else {//play aginst the computer
					try {
						newGame.playerVsComputerPlayerFirst("AI " + modeChoice.substring(19, 23), username1);//call the method and start the game with player first
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					frame.add(characterSelectionPanel);//add in the character selection for the user to get ready to start the game
					refreshFrame();
				}
			}
		});
		//action listener for when user choose to let the second player go first
		player2FirstButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(whosFirstChoicePanel);
				try {
					newGame.playerVsPlayer2First(username1, birthday1, username2, birthday2);//call the method and start the game with player 2 first
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				frame.add(characterSelectionPanel);//add in the character selection for the user to get ready to start the game
				refreshFrame();
			}
		});
		//action listener for when user choose to let the younger player go first in PVP mode
		birthdayDecideButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(whosFirstChoicePanel);
				try {
					newGame.playerVsPlayerBirthday(username1, birthday1, username2, birthday2);//call the method and start the game with the younger player go first
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				frame.add(characterSelectionPanel);//add in the character selection for the user to get ready to start the game
				refreshFrame();
			}
		});
		//action listener for when user want the program to randomly choose who goes first
		randomlyChooseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(whosFirstChoicePanel);
				if(modeChoice.startsWith("player vs player")){//play against another player 
					try {
						newGame.playerVsPlayerRandom(username1, birthday1, username2, birthday2);//call the method and start the game with randomly chosen who goes first
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					frame.add(characterSelectionPanel);//add in the character selection for the user to get ready to start the game
				}
				else {//pvc mode
					try {
						//call the method and start the game with randomly chosen who goes first between the AI and the player
						whosFirst = newGame.playerVsComputerRandom("AI " + modeChoice.substring(19, 23), username1);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					result1.setText(whosFirst);
					frame.add(characterSelectionPanel);
				}
				refreshFrame();
			}
		});
		//action listener for the ready button when the user is ready to start play the game
		readyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(characterSelectionPanel);
				if (modeChoice.startsWith("player vs player")) {//when it is PVP mode
					if (modeChoice.equals("player vs player ask questions")) {//if the user want to ask whatever questions they want during the game
						//ask
						freeAsk();
					}
					else {//when the player want to use the predefined questions
						p2pGamePreQuestion();
					}
					//add in the panel and its components for game play
					frame.add(stepPanel);
					stepPanel.add(askButton);
					stepPanel.add(next);
					stepPanel.add(guess);
					refreshFrame();
				}
				else {//when the user is play with the AI
					//start the game
					frame.add(boardPanel1);
					frame.add(stepPanel);
					refreshFrame();
					oneTurn();//call the method to start the first turn
				}
			}
		});
		//action listener for when the user is finished choosing they step, whether they want to ask question or make a guess against the AI
		stepChoiceButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				choice = (String) stepInput.getSelectedItem();
				stepPanel.remove(stepInput);
				stepPanel.remove(stepChoiceButton);
				if (choice.equals("1")) {//if user want to ask question
					stepLabel.setText("Please choice the question you want to ask: ");
					stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height of the stepLabel
					//set up the question comboBox for the user with the question the user haven't asked
					ArrayList<Question> questionsLeft = newGame.getUser1().getUnAskedQuestions();
					String[] questions = new String[questionsLeft.size()];
					for (int i = 0; i < questionsLeft.size(); i++) {
						questions[i] = questionsLeft.get(i).getQuestion();
					}
					questionComboBox = new JComboBox<String>(questions);
					questionComboBox.setBounds(490, 675, 300, 30); // x, y, width, height
					//add the questionsComboBox and questionChoiceButton
					stepPanel.add(questionComboBox);
					stepPanel.add(questionChoiceButton);
					refreshFrame();
				}
				else {//if user want to make a guess
					stepLabel.setText(newGame.getUser1().getUsername() + ", please enter your guess: ");
					stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height
					//set up a guessComboBox to store all the possible characters that the user can guess
					ArrayList<Character> validCharacters = newGame.getUser1().getGameBoard().getCharacters();
					String[] characters = new String[validCharacters.size()];
					for (int i = 0; i < validCharacters.size(); i++) {
						characters[i] = validCharacters.get(i).getName();
					}
					guessComboBox = new JComboBox<String>(characters);
					guessComboBox.setBounds(565, 675, 150, 30); // x, y, width, height
					stepPanel.add(guessComboBox);
					stepPanel.add(guessButton);
					refreshFrame();
				}
			}
		});
		//action listener when the user finish choosing the question to ask the AI
		questionChoiceButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newQuestion = (String) questionComboBox.getSelectedItem();
				String AIAnswer = newGame.AskAI(newQuestion);//store the AI's answer to user's question
				result1.setText("AI: " + AIAnswer);
				result1.setVisible(true);
				recordStepsLabel1Text += newQuestion + " : " + AIAnswer + "<br>";//record the question and the answer to the recordStepsLabel1 
				recordStepsLabel1.setText(recordStepsLabel1Text);
				newGame.getUser1().setIsTurn(false);
				newGame.getAI().setIsTurn(true);
				nextTurnButton.setVisible(true);//add in the nextTurn button for the user to move on to the next turn
				//have space of waiting period
				//skip next turn button
				preSection = "questionChoice";
			}
		});
		//action listener for  when user want to go to the next turn
		nextTurnButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (preSection.equals("questionChoice")) {//if the player just asked a question remove the questions comboBox and button
					stepPanel.remove(questionComboBox);
					stepPanel.remove(questionChoiceButton);
				}
				nextTurnButton.setVisible(false);
				result1.setVisible(false);
				oneTurn();//run next turn
			}
		});
		//action listener for the the button when user finish answering the AI's question
		questionAnswerButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				questionAnswer = (String) questionAnswerComboBox.getSelectedItem();//read the question answer
				newGame.getAI().askQuestion(AIQuestion.getQuestion(), questionAnswer);
				recordStepsLabel2Text += questionAnswer + "<br>";//store the result
				recordStepsLabel2.setText(recordStepsLabel2Text);
				newGame.getUser1().setIsTurn(true);//set the turns
				stepPanel.remove(questionAnswerButton);
				stepPanel.remove(questionAnswerComboBox);
				newGame.getAI().addQuestionAnswers(true);
				newGame.getAI().setIsTurn(false);
				nextTurnButton.setVisible(true);//add in the nextTurn button
			}
		});
		//action listener for when the player is finished selected their guess
		guessButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				finalGuess = (String) guessComboBox.getSelectedItem();//get the guess
		        resultLabel.setText(newGame.guessAI(finalGuess));
		        frame.remove(boardPanel1);
		        frame.remove(stepPanel);
		        //add in the panel for the user to enter their selected character
				ArrayList<Character> validCharacters = newGame.getUser1().getGameBoard().getCharacters();
				String[] characters = new String[validCharacters.size()];
				for (int i = 0; i < validCharacters.size(); i++) {
					characters[i] = validCharacters.get(i).getName();
				}
				charactersComboBox1= new JComboBox<String>(characters);
				inputSelectedCharacterPanel1.add(charactersComboBox1);
				inputSelectedCharacterPanel1.add(inputSelectedCharacterButton1);
		        frame.add(inputSelectedCharacterPanel1);
				refreshFrame();
			}
		});
		//action listener
		inputSelectedCharacterButton1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String userCharacterName1 = (String) charactersComboBox1.getSelectedItem();//get the character
				Character userCharacter1 = newGame.getUser1().findCharacter(userCharacterName1);//change it to Character type
				newGame.getUser1().setSelectedCharacter(userCharacter1);//set the selected character
		        player1SelectedCharacter = getCharacterImage(newGame.getUser1());//get the image of the character
				frame.remove(inputSelectedCharacterPanel1);
				newGame.setState("finished");
				if (modeChoice.startsWith("player vs computer")) {//when it is against computer
			        AISelectedCharacter = getAICharacterImage();//get the AI character image
			        //add in the information to the endingPanel
			        endingPanel.add(AISelectedCharacter);
			        endingPanel.add(resultLabel);
			        endingPanel.add(player1SelectedCharacter);
					String validateResult = "";
					if (newGame.checkUserAnswers()) {//there are not wrong answers
						validateResult = "<html>Your answer to the questions is all correct!!! <br>Thank you for doing to correctly!! :) <br>your game result will be stored";
						try {
							store = new StoreResult();
						} catch (Exception e1) {
							e1.printStackTrace();
						}
						store.addGameResultPVC(newGame.getUser1(), newGame.getAI(), newGame.getGameResult());
					}
					else {//when there are wrong answers
						ArrayList<Question> wrongAnsweredQuestion = newGame.getAI().getQuestionsAnsweredWrong();
						ArrayList<Boolean> wrongAnswers = newGame.getAI().getAnswerQuestionsAnsweredWrong();
						validateResult = "<html>you answered " + wrongAnsweredQuestion.size() + " questions wrong!!! :( <br> your game result will not be saved";//displace the number of wrong questions
						for (int i = 0; i < wrongAnsweredQuestion.size(); i ++) {//get all the questions in the arrayList
							if (wrongAnswers.get(i)) {//when the user's answer to the questions is true, add yes
								validateResult += wrongAnsweredQuestion.get(i).getQuestion() + " : yes <br>";
							}
							else {//add no
								validateResult += wrongAnsweredQuestion.get(i).getQuestion() + " : no <br>";
							}
						}
					}
					validateResult += "<html>";
					validateLabel.setText(validateResult);
					endingPanel.add(validateLabel);
			        frame.add(endingPanel, BorderLayout.CENTER);
					frame.add(recordStepsPanel1, BorderLayout.EAST);
					frame.add(recordStepsPanel2, BorderLayout.WEST);
				}
				else {//when it is against another player
					//set up another comboBox for the second user to enter their selected character
					ArrayList<Character> validCharacters = newGame.getUser1().getGameBoard().getCharacters();
					String[] characters = new String[validCharacters.size()];
					for (int i = 0; i < validCharacters.size(); i++) {
						characters[i] = validCharacters.get(i).getName();
					}
					charactersComboBox2 = new JComboBox<String>(characters);
					inputSelectedCharacterPanel2.add(charactersComboBox2);
					inputSelectedCharacterPanel2.add(inputSelectedCharacterButton2);
					frame.add(inputSelectedCharacterPanel2);
				}
				refreshFrame();
			}
		});
		//action listener for when the second player finished inputing there selected character
		inputSelectedCharacterButton2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String userCharacterName2 = (String) charactersComboBox2.getSelectedItem();//get the character
				Character userCharacter2 = newGame.getUser2().findCharacter(userCharacterName2);//change it to Character type
				newGame.getUser2().setSelectedCharacter(userCharacter2);//set the selected character
		        player2SelectedCharacter = getCharacterImage(newGame.getUser2());//get the image of the character
				frame.remove(inputSelectedCharacterPanel2);
				newGame.setState("finished");
		        endingPanel.add(player2SelectedCharacter);
		        endingPanel.add(resultLabel);
		        endingPanel.add(player1SelectedCharacter);
		        //add in the ending and steps history panels
		        frame.add(endingPanel, BorderLayout.CENTER);
				frame.add(recordStepsPanel1, BorderLayout.EAST);
				frame.add(recordStepsPanel2, BorderLayout.WEST);
				refreshFrame();
			}
		});
		//action listener for when the user is asking each other question
		askButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				User curUser = newGame.findUser(curPlayer);
				if (modeChoice.endsWith("preset questions")) {//if it is preset question user questionComboBox
					newQuestion = questionComboBox.getSelectedItem().toString();
				}
				else {//use the textField
					newQuestion = questionTextField.getText();
					questionTextField.setText("");
				}
				String question = curPlayer + ", " + newQuestion;//get the question
				//pop up window to ask the user question
				int result = JOptionPane.showConfirmDialog(null, question, "Confirmation", JOptionPane.YES_NO_OPTION);
		        if (result == JOptionPane.YES_OPTION) {// User chose YES
		    		curUser.setQuestionAsked(newQuestion);//set the question asked for the user to the nestOne
					if(curPlayer.equals(username1)){//when it is player 1 asking
						recordStepsLabel1Text += newQuestion+"  "+"yes.<br>";//add to Label 1
					}
					else{//player 2 asking
						recordStepsLabel2Text += newQuestion+"  "+"yes.<br>";//Label 2
					}
					result1.setText("yes");//displace the result on the frame
		        }
		        else {// User chose NO
					if(curPlayer.equals(username1)){//when it is player 1 asking
						recordStepsLabel1Text+=newQuestion+"  "+"no.<br>";
					}
					else {//player 2 asking
						recordStepsLabel2Text+=newQuestion+"  "+"no.<br>";
					}
					result1.setText("no");
		        }
				recordStepsLabel1.setText(recordStepsLabel1Text);
				recordStepsLabel2.setText(recordStepsLabel2Text);
				askButton.setEnabled(false);
			}
		});
		//action listener the user use to guess the otherplayed character in pvp mode
		guess.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (newGame.getUser1().getIsTurn()) {//user1's turn
					frame.remove(boardPanel1);
				}
				else {//user2's turn
					frame.remove(boardPanel2);
				}
				frame.remove(stepPanel);
				frame.add(guessBoardPanel);
				refreshFrame();
			}
		});
		//action listener the button the users use to switch turn in pvp modes
		next.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (curPlayer.equals(username1)) {//when it is userboad's board
					//remove the board1 and board2 need to remove and add the stepPanel for it to work 
					frame.remove(boardPanel1);
					frame.remove(stepPanel);
					frame.add(boardPanel2);
					frame.add(stepPanel);
					newGame.getUser1().setIsTurn(false);
					newGame.getUser2().setIsTurn(true);
				    curPlayer = username2;//change curPlayer
				}
				else {//when it is userboad's board
					//remove the board2 and board1 need to remove and add the stepPanel for it to work 
					frame.remove(boardPanel2);
					frame.remove(stepPanel);
					frame.add(boardPanel1);
					frame.add(stepPanel);
					newGame.getUser1().setIsTurn(true);
					newGame.getUser2().setIsTurn(false);
				    curPlayer = username1;//change curPlayer
				}
				stepLabel.setText(curPlayer + ", Choose to ask a question or guess the answer: ");
				result1.setText("");
				askButton.setEnabled(true);
				refreshFrame();
			}
		});
	}
	/**
	 * this method will run the code for one turn of the game, which could be player's turn or AI's. 
	 */
	private void oneTurn() {
		if (newGame.getUser1().getIsTurn()) {//player turn
			stepLabel.setText("Please make your choice: 1. ask question. 2. guess the character");
			stepPanel.add(stepInput);
			stepPanel.add(stepChoiceButton);
			refreshFrame();
		}
		else {//AI turn
			if (newGame.getAI().onlyOne()) {//if there is only one possible character left
				String question = "Is " + newGame.getAI().lastOne() + " the character? ";//get the last character in the 
				int result = JOptionPane.showConfirmDialog(null, question, "Confirmation", JOptionPane.YES_NO_OPTION);
		        if (result == JOptionPane.YES_OPTION) {// User chose YES
		        	String ans = newGame.getAI().playGuess(username1, false);
		        	resultLabel.setText(ans);
		        	JOptionPane.showMessageDialog(null, ans, "Message", JOptionPane.INFORMATION_MESSAGE);
					newGame.setGameResult("AI");
		        }
		        else {// User chose NO
		        	String ans = newGame.getAI().playGuess(username1, true);
		        	resultLabel.setText(ans);
		        	JOptionPane.showMessageDialog(null, ans, "Message", JOptionPane.INFORMATION_MESSAGE);
		        	newGame.setGameResult(username1);
		        }
		        newGame.setState("finished");
		        //remove the board and the steps
		        frame.remove(boardPanel1);
		        frame.remove(stepPanel);
		        //set up the comboBox
				ArrayList<Character> validCharacters = newGame.getUser1().getGameBoard().getCharacters();
				String[] characters = new String[validCharacters.size()];
				for (int i = 0; i < validCharacters.size(); i++) {
					characters[i] = validCharacters.get(i).getName();
				}
				charactersComboBox1 = new JComboBox<String>(characters);
				inputSelectedCharacterPanel1.add(charactersComboBox1);
				inputSelectedCharacterPanel1.add(inputSelectedCharacterButton1);
		        frame.add(inputSelectedCharacterPanel1);
				refreshFrame();
			}
			else {// there are morn than one possible characters
				AIQuestion = newGame.getAI().playQuestion();//get the question
				String choosenQuestion = AIQuestion.getQuestion();
				//displace the question
				stepLabel.setText(choosenQuestion);
				stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height
				stepLabel.setVisible(true);
				stepPanel.add(questionAnswerButton);
				stepPanel.add(questionAnswerComboBox);
				//store the result
				recordStepsLabel2Text += choosenQuestion + " : ";
				recordStepsLabel2.setText(recordStepsLabel2Text);
				refreshFrame();
			}
		}
	}
	/**
	 * this method will use the inputed user to find and output the image of the selected character icon
	 * @param player the user that was inputed
	 * @return the JLabel with the selected Character icon
	 */
	private JLabel getCharacterImage(User player) {
        int characterIndex = player.getSelectedCharacter().getCharacterIndex();
        ImageIcon characterIcon = characterImages.get(characterIndex);
		JLabel characterLabel = new JLabel(characterIcon);
		return characterLabel;
	}
	/**
	 * this method will get the image of the selected image of the AI
	 * @return it will return a JLabel with the image of the AI's selected character
	 */
	private JLabel getAICharacterImage() {
        int characterIndex = newGame.getAI().getSelectedCharacter().getCharacterIndex();
        ImageIcon characterIcon = characterImages.get(characterIndex);
		JLabel characterLabel = new JLabel(characterIcon);
		return characterLabel;
	}
	/**
	 * this method will be flipping or changing the character buttons when it is clicked
	 * @param button the button that was clicked
	 * @param iconStates the array what stores the state of the button
	 * @param index the index of the button in the array
	 */
	private void newButtonIcon(JButton button, ArrayList<String> iconStates, int index) {
		if (iconStates.get(index).equals("front")) {
			button.setIcon(back);//button.setIcon(newIcon); change the icon
			iconStates.set(index, "back");
		}
		else {
			ImageIcon characterIcon = characterImages.get(index);
			button.setIcon(characterIcon);//button.setIcon(newIcon); change the icon
			iconStates.set(index, "front");
		}
	}
	/**
	 * repaint the frame
	 */
	private void refreshFrame() {
		frame.revalidate();
		frame.repaint();
	}
	/**
	 * this method will add in the choosing who goes first buttons for playing against another player
	 */
	private void askUsernamePVP() {
		frame.remove(modeChoicePanel);
		// Get the selected item
		frame.add(usernameAskingPanel1, BorderLayout.CENTER);
		whosFirstChoicePanel.add(player1FirstButton);
		whosFirstChoicePanel.add(player2FirstButton);
		whosFirstChoicePanel.add(birthdayDecideButton);
		whosFirstChoicePanel.add(randomlyChooseButton);
		refreshFrame();
	}
	/**
	 * this method will add in the choosing who goes first buttons for playing against AI
	 */
	private void askUsernamePVC() {
		frame.remove(modeChoicePanel);
		// Get the selected item
		frame.add(usernameAskingPanel1, BorderLayout.CENTER);
		whosFirstChoicePanel.add(player1FirstButton);
		whosFirstChoicePanel.add(AIFirstButton);
		whosFirstChoicePanel.add(randomlyChooseButton);
		refreshFrame();
	}
	/**
	 * this method will be called when the it pvp predefined question mode is starting, it add in all the boards and panels
	 */
	private void p2pGamePreQuestion() {
		if (newGame.getUser1().getIsTurn()) {
			frame.add(boardPanel1);
			curPlayer = username1;
		}
		else {
			frame.add(boardPanel2);
			curPlayer = username2;
		}

		stepLabel.setText(curPlayer + ", Choose a question or guess the character");
		//creating the questionComboBox
		ArrayList<Question> questionsLeft = newGame.getUser1().getUnAskedQuestions();
		String[] questions = new String[questionsLeft.size()];
		for (int i = 0; i < questionsLeft.size(); i++) {
			questions[i] = questionsLeft.get(i).getQuestion();
		}
		questionComboBox = new JComboBox<String>(questions);
		questionComboBox.setBounds(490, 675, 300, 30); // x, y, width, height
		//add the questionsComboBox
		stepPanel.add(questionComboBox);

		result1.setText("");
	}
	/**
	 * this method will be run when the pvp ask question game is starting, it set up the board and question asking
	 */
	private void freeAsk() {
		if (newGame.getUser1().getIsTurn()) {
			frame.add(boardPanel1);
			curPlayer = username1;
		}
		else {
			frame.add(boardPanel2);
			curPlayer = username2;
		}

		stepLabel.setText(curPlayer + ", input a question or guess the character (don't make the question go over 43 letters including space)");
//		stepLabel.setBounds(200, 900, 850, 60);
		questionTextField = new JTextField();//43 max
		questionTextField.setBounds(490, 675, 300, 30); // x, y, width, height
		//add the questionTextField
		stepPanel.add(questionTextField);

		result1.setText("");
	}
	/**
	 * this method will have a pop up window to ask the other player wether the first player's guess is right or wrong
	 * depend on the answer store the result in a label
	 * add in the in panel that ask the users to input the selected character
	 * @param user1 the user that is guessing
	 * @param user2 the user that is answering question
	 * @param index the index of the character of the user1's guess
	 */
	private void guessPVP(User user1, User user2, int index) {
		ArrayList<Character> validCharacters = user1.getGameBoard().getCharacters();//get the guessed character
		String question = "Is " + validCharacters.get(index).getName() + " the character? ";//question statement
		int result = JOptionPane.showConfirmDialog(null, question, "Confirmation", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {// User chose YES
        	resultLabel.setText("Congraulation, " + user1.getUsername() + " you guessed the character, you won!!!!");
        	newGame.setGameResult(user1.getUsername());
        }
        else {// User chose NO
        	resultLabel.setText("<html>Congraulation, " + user2.getUsername() + ", you won!!!! <br>Because " + user1.getUsername() + " you guessed the wrong character<html>");
        	newGame.setGameResult(user2.getUsername());
        }
        newGame.setState("finished");
		String[] characters = new String[validCharacters.size()];
		for (int i = 0; i < validCharacters.size(); i++) {
			characters[i] = validCharacters.get(i).getName();
		}
		charactersComboBox1 = new JComboBox<String>(characters);
		inputSelectedCharacterPanel1.add(charactersComboBox1);
		inputSelectedCharacterPanel1.add(inputSelectedCharacterButton1);
        frame.add(inputSelectedCharacterPanel1);
		refreshFrame();
	}
	/**
	 * this method will read the eliminated character icon and storing it
	 */
	private void getBackIcon() {
		ImageIcon characterIcon = new ImageIcon(getClass().getResource("characterGone.jpg"));
		Image image = characterIcon.getImage();
		Image newimg = image.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH);
		characterIcon = new ImageIcon(newimg);
		back = characterIcon;
	}
	/**
	 * this method will read the images and stored them and it will only be called once in the beginning of the program
	 */
	private void readAllImages() {
		//get all the images for the characters by reading
		for (int i = 0; i < 24; i++) {
			ImageIcon characterIcon = new ImageIcon(getClass().getResource("" + i + ".jpg"));
			Image image = characterIcon.getImage();
			Image newimg = image.getScaledInstance(width, height,  java.awt.Image.SCALE_SMOOTH);
			characterIcon = new ImageIcon(newimg);
			characterImages.add(characterIcon);
		}
		//read the image for the characters that were elimated
		getBackIcon();
	}
	public static void main(String[] args) {
		//uploading the music
		try {
			File audioFile = new File("Bloom of Youth.wav");
			AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
			music = AudioSystem.getClip();
			music.open(audioStream);
		}
		catch (Exception e) {}
		music.start();
		music.loop(Clip.LOOP_CONTINUOUSLY);//keep repeating the music
		//run the GUI
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new GUI();
			}
		});
	}
}