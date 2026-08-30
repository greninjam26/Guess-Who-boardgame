package com.guesswho.game;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Works out which board question someone meant when they typed their own.
 *
 * <p>The computer can only answer questions it has the data for, so a typed
 * question has to resolve to one of the board's. That is not open-ended
 * understanding: every board question carries a category and a value —
 * {@code eyeColour, Blue} or {@code glasses, TRUE} — so this is a matter of
 * recognising ten attributes and nineteen values.</p>
 *
 * <p>When it cannot tell, it says so. Some text genuinely has no answer:
 * {@code brown} is both an eye colour and a hair colour, and nothing on the
 * board answers "do they look friendly?". Guessing would make the player
 * eliminate the wrong characters and lose a game they should have won, with
 * nothing on screen to explain why, so an unresolved question is returned empty
 * and the player asks again.</p>
 */
public final class TypedQuestion {
    /** Words that point at a category of question. */
    private static final Map<String, List<String>> CATEGORY_WORDS = Map.of(
            "eyeColour", List.of("eye", "eyes"),
            "gender", List.of("male", "female", "man", "woman", "boy", "girl", "gender"),
            "skinTone", List.of("skin", "complexion"),
            "hairColour", List.of("hair", "haired"),
            "facialHair", List.of("facial hair", "beard", "bearded", "moustache",
                    "mustache", "stubble"),
            "glasses", List.of("glasses", "spectacles", "specs"),
            "teethVisibility", List.of("teeth", "tooth", "smiling", "smile"),
            "wearingHat", List.of("hat", "cap", "headwear"),
            "hairLength", List.of("hair", "haired", "bald", "ponytail", "tied"),
            "isPiercings", List.of("piercing", "piercings", "earring", "earrings"));

    /** Words that point at one value within a category. */
    private static final Map<String, List<String>> VALUE_WORDS = Map.of(
            "Blue", List.of("blue"),
            "Brown", List.of("brown"),
            "Green", List.of("green"),
            "Black", List.of("black"),
            "Ginger", List.of("ginger", "red", "redhead"),
            "Blonde", List.of("blonde", "blond", "fair"),
            "White", List.of("white", "grey", "gray", "silver"),
            "Short", List.of("short"),
            "Long", List.of("long"));

    private static final Map<String, List<String>> REMAINING_VALUE_WORDS = Map.of(
            "Tied Up", List.of("tied up", "ponytail", "tied"),
            "Bald", List.of("bald"));

    private final List<Question> boardQuestions;

    /**
     * Matches against the questions a board offers.
     *
     * @param boardQuestions the board's preset questions
     */
    public TypedQuestion(List<Question> boardQuestions) {
        this.boardQuestions = List.copyOf(boardQuestions);
    }

    /**
     * Finds the board question a typed one is asking.
     *
     * @param typed what the player wrote
     * @return the matching board question, or empty when it cannot be told
     */
    public Optional<Question> resolve(String typed) {
        if (typed == null || typed.isBlank()) {
            return Optional.empty();
        }
        String text = typed.toLowerCase(Locale.ROOT);

        //Categories overlap on purpose: "hair" belongs to both colour and
        //length, so the value is what tells them apart. A question matches only
        //when the text names its attribute and, where the attribute has several
        //values, that value too.
        List<Question> matches = boardQuestions.stream()
                .filter(question -> asks(text, question))
                .toList();
        //Nothing recognised, or two different questions asked at once: either
        //way, choosing one risks answering a question nobody asked.
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private boolean asks(String text, Question question) {
        if (!mentions(text, CATEGORY_WORDS.getOrDefault(question.getCategory(), List.of()))) {
            return false;
        }
        List<String> values = valueWords(question.getAttribute());
        return values.isEmpty() || mentions(text, values);
    }

    private static List<String> valueWords(String attribute) {
        return VALUE_WORDS.getOrDefault(attribute,
                REMAINING_VALUE_WORDS.getOrDefault(attribute, List.of()));
    }

    private static boolean mentions(String text, List<String> words) {
        return words.stream().anyMatch(word -> containsWord(text, word));
    }

    //java.lang.Character in full: this package has a Character of its own,
    //which shadows it.
    /** Whole words only, so "hat" does not match inside "that". */
    private static boolean containsWord(String text, String word) {
        int from = 0;
        while (true) {
            int at = text.indexOf(word, from);
            if (at < 0) {
                return false;
            }
            boolean startsCleanly = at == 0 || !java.lang.Character.isLetter(text.charAt(at - 1));
            int after = at + word.length();
            boolean endsCleanly = after == text.length()
                    || !java.lang.Character.isLetter(text.charAt(after));
            if (startsCleanly && endsCleanly) {
                return true;
            }
            from = at + 1;
        }
    }
}
