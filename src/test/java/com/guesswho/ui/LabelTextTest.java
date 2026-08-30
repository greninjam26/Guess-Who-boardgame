package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LabelTextTest {
    @Test
    void escapesTheCharactersThatWouldBeReadAsMarkup() {
        assertEquals("a &amp; b &lt;c&gt;", LabelText.escaped("a & b <c>"));
    }

    @Test
    void escapesAmpersandsBeforeTheEntitiesItIntroduces() {
        assertEquals("&amp;lt;", LabelText.escaped("&lt;"),
                "Escaping < first would turn its own output into a live entity");
    }

    @Test
    void neutralisesATagInAUsername() {
        assertEquals("&lt;b&gt;Alex&lt;/b&gt;", LabelText.escaped("<b>Alex</b>"));
    }

    @Test
    void leavesOrdinaryTextAlone() {
        assertEquals("Alex", LabelText.escaped("Alex"));
    }

    @Test
    void treatsMissingTextAsEmpty() {
        assertEquals("", LabelText.escaped(null));
    }
}
