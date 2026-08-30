package com.guesswho.ui;

/**
 * Helpers for text that ends up in a Swing label.
 *
 * <p>Labels render a subset of HTML, which is the only way to break a line in
 * one. That makes any player-supplied text — a username, a typed question —
 * markup unless it is escaped first. Today a name containing a tag only renders
 * oddly; once names arrive from a server chosen by other people, a label would
 * honour whatever they put in one.</p>
 */
final class LabelText {
    private LabelText() {
    }

    /**
     * Escapes text so a label renders it as characters rather than markup.
     *
     * @param text untrusted text, possibly {@code null}
     * @return the text safe to place inside an HTML label
     */
    static String escaped(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
