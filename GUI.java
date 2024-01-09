import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class GUI {
	// Main frame of the application
	private JFrame frame;
	private Game newGame;
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
	public GUI() {
		gameUI();
	}
	private void gameUI() {
		frame = new JFrame("Guess Who? Game");
		frame.setPreferredSize(new Dimension(700, 1200));// Width: 700 pixels, Height: 900 pixels
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());

		newGame = new Game();
		// Game panel with character buttons
		JPanel boardPanel = new JPanel(null); // 4 rows and 6 columns for 24 characters
		int width = 100;
		int height = 150;
		boardPanel.setBounds(0, 30, 700, height*4+3*5);
		for (int i = 0; i < 24; i++) {
			ImageIcon characterIcon = new ImageIcon(getClass().getResource("" + i + ".jpg"));
			Image image = characterIcon.getImage();
			Image newimg = image.getScaledInstance(width, height,  java.awt.Image.SCALE_SMOOTH);
			characterIcon = new ImageIcon(newimg);
			JButton characterButton = new JButton(characterIcon);
			int x = width*(i%6) + 14*(i%6+1);
			int y = height*(i/6) + 5*(i/6+1);
			characterButton.setBounds(x, y, width, height);
			// Add action listeners to character buttons here if needed
			boardPanel.add(characterButton);
		}
		
		JPanel quitPanel = new JPanel();
		JButton quitButton = new JButton("Quit");
		
		JPanel welcomePanel = new JPanel();
		JLabel welcomeLabel = new JLabel("Welcme to the Guess Who? Board Game!!");
		
		JPanel modeChoicePanel = new JPanel();
		JLabel modeChoiceLabel = new JLabel("Please choose your game mode: ");
		String[] modeChoices = {"player vs computer easy mode", 
								"player vs computer hard mode", 
								"player vs player reset questions",
								"player vs player ask questions"
		};
		JComboBox<String> modeChoicesComboBox = new JComboBox<>(modeChoices);
		JButton modeChoiceButton = new JButton("Comfirm");
		
		usernameAskingPanel1 = new JPanel();
		JLabel usernameAskingLabel1 = new JLabel("Please enter your username: ");
		usernameField1 = new JTextField(20);
		usernameAskingButton1 = new JButton("Comfirm");
		
		birthdayAskingPanel1 = new JPanel();
		JLabel birthdayAskingLabel1 = new JLabel("Please enter your birthday in the form of(YYYYMMDD): ");
		birthdayField1 = new JTextField(20);
		birthdayAskingButton1 = new JButton("Comfirm");

		usernameAskingPanel2 = new JPanel();
		JLabel usernameAskingLabel2 = new JLabel("Second player, please enter your username: ");
		usernameField2 = new JTextField(20);
		usernameAskingButton2 = new JButton("Comfirm");
		
		birthdayAskingPanel2 = new JPanel();
		JLabel birthdayAskingLabel2 = new JLabel("Please enter your birthday in the form of(YYYYMMDD): ");
		birthdayField2 = new JTextField(20);
		birthdayAskingButton2 = new JButton("Comfirm");
		
        stepPanel = new JPanel(null);
		stepLabel = new JLabel("Please make your choice: 1. ask question. 2. guess the character");
		stepLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center the label text
		stepLabel.setBounds(50, 625, 600, 30); // x, y, width, height
		String[] stepChoice = {"1", "2"};
		stepInput = new JComboBox<>(stepChoice);
		stepInput.setBounds(300, 675, 75, 30); // x, y, width, height
		stepChoiceButton = new JButton("Comfirm");
		stepChoiceButton.setBounds(375, 675, 100, 30); // x, y, width, height
		questionChoiceButton = new JButton("Comfirm");
		questionChoiceButton.setBounds(450, 675, 100, 30); // x, y, width, height
		guessButton = new JButton("Guess");
		guessButton.setBounds(375, 675, 100, 30); // x, y, width, height
		result1 = new JLabel("");
		result1.setBounds(0, 705, 700, 30); // x, y, width, height
		result1.setHorizontalAlignment(SwingConstants.CENTER); // Center the label text
		String[] questionChoices = {"yes", "no"};
		questionAnswerComboBox = new JComboBox<String>(questionChoices);
		questionAnswerComboBox.setBounds(300, 675, 75, 30); // x, y, width, height
		questionAnswerButton = new JButton("Confirm");
		questionAnswerButton.setBounds(375, 675, 100, 30); // x, y, width, height
		JButton nextTurnButton = new JButton("next turn");
		nextTurnButton.setBounds(550, 705, 100, 30); // x, y, width, height
		
		JPanel recordStepsPanel = new JPanel();
		JLabel recordStepsHeadPanel = new JLabel();
		
		welcomePanel.add(welcomeLabel);
		quitPanel.add(quitButton);
		modeChoicePanel.add(modeChoiceLabel);
		modeChoicePanel.add(modeChoicesComboBox);
		modeChoicePanel.add(modeChoiceButton);
		usernameAskingPanel1.add(usernameAskingLabel1);
		usernameAskingPanel1.add(usernameField1);
		usernameAskingPanel1.add(usernameAskingButton1);
		birthdayAskingPanel1.add(birthdayAskingLabel1);
		birthdayAskingPanel1.add(birthdayField1);
		birthdayAskingPanel1.add(birthdayAskingButton1);
		usernameAskingPanel2.add(usernameAskingLabel2);
		usernameAskingPanel2.add(usernameField2);
		usernameAskingPanel2.add(usernameAskingButton2);
		birthdayAskingPanel2.add(birthdayAskingLabel2);
		birthdayAskingPanel2.add(birthdayField2);
		birthdayAskingPanel2.add(birthdayAskingButton2);
		stepPanel.add(stepLabel);
		stepPanel.add(result1);
		stepPanel.add(nextTurnButton);
		nextTurnButton.setVisible(false);
		
		// Add panels to the frame
		frame.add(welcomePanel, BorderLayout.NORTH);
		frame.add(quitPanel, BorderLayout.SOUTH);
		frame.add(usernameAskingPanel1, BorderLayout.CENTER);
		// Show the frame
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		quitButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				 frame.dispose();
			}
		});
		usernameAskingButton1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				username1 = usernameField1.getText();
				frame.remove(usernameAskingPanel1);
				frame.add(modeChoicePanel, BorderLayout.CENTER);
				frame.revalidate();
				frame.repaint();
			}
		});
		modeChoiceButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.remove(modeChoicePanel);
				// Get the selected item
				modeChoice = (String) modeChoicesComboBox.getSelectedItem();
				if (modeChoice.substring(0, 16).equals("player vs player")) {
					frame.add(birthdayAskingPanel1, BorderLayout.CENTER);
					frame.revalidate();
					frame.repaint();
				}
				else {
					try {
						whosFirst = newGame.playerVsComputer("AI " + modeChoice.substring(19, 23), username1);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					stepLabel.setText(whosFirst);
					System.out.println(whosFirst);
					frame.add(boardPanel);
					frame.add(stepPanel);
					frame.revalidate();
					frame.repaint();
					oneTurn();
				}
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
					stepLabel.setBounds(50, 625, 600, 30); // x, y, width, height
					ArrayList<Question> questionsLeft = newGame.getUser1().getUnAskedQuestions();
					String[] questions = new String[questionsLeft.size()];
					for (int i = 0; i < questionsLeft.size(); i++) {
						questions[i] = questionsLeft.get(i).getQuestion();
					}
					questionComboBox = new JComboBox<String>(questions);
					questionComboBox.setBounds(150, 675, 300, 30); // x, y, width, height
					stepPanel.add(questionComboBox);
					stepPanel.add(questionChoiceButton);
					frame.revalidate();
					frame.repaint();
				}
				else {
					stepLabel.setText(newGame.getUser1().getUsername() + ", please enter your guess: ");
					stepLabel.setBounds(50, 625, 600, 30); // x, y, width, height
					ArrayList<Character> validCharacters = newGame.getUser1().getGameBoard().getCharacters();
					String[] characters = new String[validCharacters.size()];
					for (int i = 0; i < validCharacters.size(); i++) {
						characters[i] = validCharacters.get(i).getName();
					}
					guessComboBox = new JComboBox<String>(characters);
					guessComboBox.setBounds(225, 675, 150, 30); // x, y, width, height
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
				stepPanel.remove(questionComboBox);
				stepPanel.remove(questionChoiceButton);
				result1.setText("AI: " + newGame.AskAI(newQuestion));
				result1.setVisible(true);
				newGame.getUser1().setIsTurn(false);
				newGame.getAI().setIsTurn(true);
				nextTurnButton.setVisible(true);
				//have space of waiting period
				//skip next turn button
			}
		});
		nextTurnButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
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
				stepPanel.remove(questionAnswerButton);
				stepPanel.remove(questionAnswerComboBox);
				newGame.getUser1().setIsTurn(true);
				newGame.getAI().setIsTurn(false);
				nextTurnButton.setVisible(true);
			}
		});
		guessButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				finalGuess = (String) guessComboBox.getSelectedItem();
				stepPanel.remove(guessComboBox);
				stepPanel.remove(guessButton);
				result1.setText(newGame.guessAI(finalGuess));
				result1.setVisible(true);
				frame.revalidate();
				frame.repaint();
				newGame.setState("finished");
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
		else {
			if (newGame.getAI().onlyOne()) {
				String question = "Is " + newGame.getAI().onlyOneLeft() + " the character? ";
				int result = JOptionPane.showConfirmDialog(null, question, "Confirmation", JOptionPane.YES_NO_OPTION);
		        if (result == JOptionPane.YES_OPTION) {// User chose YES
		        	String ans = newGame.getAI().playGuess(username1, false);
		        	JOptionPane.showMessageDialog(null, ans, "Message", JOptionPane.INFORMATION_MESSAGE);
		        }
		        else {// User chose NO
		        	String ans = newGame.getAI().playGuess(username1, true);
		        	JOptionPane.showMessageDialog(null, ans, "Message", JOptionPane.INFORMATION_MESSAGE);
		        }
		        newGame.setState("finished");
			}
			else {
				AIQuestion = newGame.getAI().playQuestion();
				stepLabel.setText(AIQuestion.getQuestion());
				stepLabel.setBounds(50, 625, 600, 30); // x, y, width, height
				stepLabel.setVisible(true);
				stepPanel.add(questionAnswerButton);
				stepPanel.add(questionAnswerComboBox);
				frame.revalidate();
				frame.repaint();
			}
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