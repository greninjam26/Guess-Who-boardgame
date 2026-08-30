package com.guesswho.ui;

/**
 * Long-form text shown by the setup screens, kept apart so the screen code
 * stays readable.
 */
final class SetupText {
    private SetupText() {
    }

    //No inline font: the look and feel picks one, and hard-coding a family here
    //would leave this one screen ignoring it.
    /** How-to-play text, shown in its own window from the welcome screen. */
    static final String INSTRUCTIONS = """
            <html><body>
            <h2>Guess Who?</h2>
            <p>Each player holds one of twenty-four characters. You win by working
            out your opponent's before they work out yours.</p>

            <h3>Taking a turn</h3>
            <p>Ask a question your opponent can answer yes or no, such as
            <i>"Does your character wear glasses?"</i>. Their answer rules out
            everyone it does not fit.</p>
            <p>Click a character on your board to flip them face down once you
            have ruled them out. Click again if you change your mind.</p>
            <p>When you think you know, choose <b>Guess</b> and pick them. A
            correct guess wins; a wrong one loses.</p>

            <h3>Choosing your character</h3>
            <p>You are asked which character you are holding before play begins.
            Your opponent never sees it, and neither does the person sitting next
            to you if they look away.</p>
            <p>Telling the game up front lets it check afterwards that every
            answer you gave really did match. If you would rather keep it to
            yourself, tick the box on that screen and you will be asked once the
            game is over instead — the check still runs, but it can only show
            your answers were consistent, not that you settled on a character
            before the questions started.</p>

            <h3>Game modes</h3>
            <p><b>Against the computer</b> on easy or hard. Hard narrows the
            field faster by choosing questions that rule out about half the
            remaining characters.</p>
            <p><b>Against another player</b> on this machine, taking turns. You
            can use the board's questions or type your own — the computer can
            only answer the board's, so free questions are for two players.</p>

            <h3>Afterwards</h3>
            <p>Completed games are sent to the server and counted on the
            leaderboard, which keeps a separate board for games against the
            computer and games against another player.</p>
            </body></html>
            """;
}
