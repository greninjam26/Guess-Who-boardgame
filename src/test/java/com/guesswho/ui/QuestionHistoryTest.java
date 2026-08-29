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
    void escapesMarkupInAnEntry() {
        assertEquals("a &amp; b &lt;c&gt;", QuestionHistory.escaped("a & b <c>"));
    }

    @Test
    void treatsMissingTextAsEmpty() {
        assertEquals("", QuestionHistory.escaped(null));
    }
}
