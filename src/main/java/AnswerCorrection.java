/**
 * Describes one answer that did not match the human player's selected
 * character.
 *
 * @param question question text asked by the computer
 * @param expectedAnswer answer implied by the selected character
 */
public record AnswerCorrection(String question, boolean expectedAnswer) {
}
