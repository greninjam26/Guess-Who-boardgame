package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuestionHistoryTest {
    @Test
    void namesBothParticipants() {
        QuestionHistory history = new QuestionHistory();

        history.begin("Alex", "AI");

        assertTrue(history.firstText().contains("Alex: "), history.firstText());
        assertTrue(history.secondText().contains("AI: "), history.secondText());
    }

    @Test
    void keepsTheTwoTranscriptsApart() {
        QuestionHistory history = new QuestionHistory();
        history.begin("Alex", "Blake");

        history.recordForFirst("Glasses?  yes.");

        assertTrue(history.firstText().contains("Glasses?"));
        assertFalse(history.secondText().contains("Glasses?"));
    }

    @Test
    void keepsEveryEntryInOrder() {
        QuestionHistory history = new QuestionHistory();
        history.begin("Alex", "Blake");

        history.recordForFirst("first");
        history.recordForFirst("second");

        assertTrue(history.firstText().indexOf("first") < history.firstText().indexOf("second"));
    }

    @Test
    void startsEachGameFromEmpty() {
        QuestionHistory history = new QuestionHistory();
        history.begin("Alex", "Blake");
        history.recordForFirst("from the last game");

        history.begin("Casey", "Drew");

        assertFalse(history.firstText().contains("from the last game"),
                "Restarting must not carry the previous game's transcript over");
        assertFalse(history.firstText().contains("Alex"));
    }

    @Test
    void rendersAsAClosedHtmlDocument() {
        QuestionHistory history = new QuestionHistory();
        history.begin("Alex", "Blake");

        assertTrue(history.firstText().startsWith("<html>"));
        assertTrue(history.firstText().endsWith("</html>"));
    }

    @Test
    void escapesMarkupInAName() {
        QuestionHistory history = new QuestionHistory();

        history.begin("<b>Alex", "Blake");

        assertTrue(history.firstText().contains("&lt;b&gt;Alex"), history.firstText());
        assertFalse(history.firstText().contains("<b>"),
                "A name must not be rendered as markup by the label showing it");
    }

    @Test
    void handsBackTheTranscriptsAndTakesThemAgain() throws Exception {
        QuestionHistory history = new QuestionHistory();
        history.begin("sam", "alex");
        history.recordForFirst("Does your character wear glasses? Yes");
        history.recordForSecond("Is the person wearing a hat? No");

        String first = history.firstEntries();
        String second = history.secondEntries();
        QuestionHistory resumed = new QuestionHistory();
        resumed.restore(first, second);

        assertEquals(history.firstText(), resumed.firstText());
        assertEquals(history.secondText(), resumed.secondText());
        assertTrue(resumed.firstText().contains("glasses"),
                "The transcript is the record of how the position was reached");
    }

    @Test
    void restoringNothingLeavesAnEmptyTranscriptRatherThanFailing() {
        QuestionHistory history = new QuestionHistory();

        history.restore(null, null);

        assertEquals("<html></html>", history.firstText());
    }
}
