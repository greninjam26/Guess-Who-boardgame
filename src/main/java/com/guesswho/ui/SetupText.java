package com.guesswho.ui;

/**
 * Long-form text shown by the setup screens, kept apart so the screen code
 * stays readable.
 */
final class SetupText {
    private SetupText() {
    }

    /** How-to-play text shown on the welcome screen. */
    static final String INSTRUCTIONS = "<html><body style='font-family:Arial; font-size:14;'>" +
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
                   "Completed games are sent to the server and included in the leaderboard. " +
                   "In the game, you can ask a yes or no question about your opponent's characters using the " +
                   "<strong>\"Ask Question\"</strong> button. <br>When you wish to guess who your opponent character is, click the " +
                   "<strong>\"Guess\"</strong> button, then select a character on the board. Characters can be flipped down by clicking <br>" +
                   "on their icons on the board.<br>" +
                   "<br>We hope you enjoy the game!<br>" +
                   "</body></html>";
}
