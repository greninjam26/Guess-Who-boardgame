import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Writes the game's background track.
 *
 * <p>Generated rather than sourced, for the same reason the character art is:
 * a recording carries two separate rights, the composition and the performance,
 * and neither is mine to put inside an installer. This is arithmetic, so there
 * is nothing to license.</p>
 *
 * <p>It is written to loop for as long as someone leaves the game open, so it
 * stays deliberately gentle: warm chords under changing arpeggios, with short
 * answering phrases that appear only occasionally. There is enough movement to
 * avoid sounding mechanical, but no percussion or foreground melody to compete
 * with a player who is thinking.</p>
 *
 * <pre>java tools/BackgroundTrack.java</pre>
 */
public final class BackgroundTrack {
    private static final float RATE = 44100;
    /** Mono: the file is uncompressed, and stereo would double it for no gain here. */
    private static final int CHANNELS = 1;

    private static final double BAR_SECONDS = 2.4;
    private static final int BARS = 32;

    private static final Path TARGET =
            Path.of("game-core/src/main/resources/audio/Guessing Game.wav");

    /**
     * Semitones from middle C. Four eight-bar sections tell one quiet harmonic
     * story instead of restarting the same four bars throughout the track.
     */
    private static final int[][] HARMONY = {
        //Opening: establish home, then leave a question hanging on G7.
        {0, 4, 7, 11}, {-3, 0, 4, 7}, {-7, -3, 0, 4}, {-5, -1, 2, 5},
        {4, 7, 11, 14}, {-3, 0, 4, 7}, {2, 5, 9, 12}, {-5, -1, 2, 5},
        //Playful answer: inversions keep the bass from walking the same route.
        {4, 7, 11, 12}, {-7, -3, 0, 4}, {0, 4, 7, 11}, {-1, 2, 7, 11},
        {-3, 0, 4, 7}, {7, 11, 14, 16}, {-7, -3, 0, 7}, {-5, -1, 2, 5},
        //Breathing room: a softer journey away from the opening pattern.
        {-7, -3, 0, 4}, {4, 7, 12, 16}, {2, 5, 9, 12}, {-3, 0, 4, 7},
        {-7, -3, 0, 4}, {4, 7, 12, 16}, {2, 5, 9, 12}, {-5, 0, 2, 5},
        //Return: recognisable harmony, new movement, then a clean lead into C.
        {0, 4, 7, 11}, {-1, 2, 7, 11}, {-3, 0, 4, 7}, {4, 7, 11, 14},
        {-7, -3, 0, 4}, {4, 7, 12, 16}, {2, 5, 9, 12}, {-5, -1, 2, 5},
    };

    /** Chord degrees; four and above move that degree up an octave, -1 is a rest. */
    private static final int[][] ARPEGGIOS = {
        {0, 1, 2, 3, 6, 5, 3, 1},
        {0, 2, 1, 3, 5, -1, 2, 7},
        {0, -1, 2, -1, 1, -1, 3, -1},
        {0, 1, 6, 1, 3, 2, 5, 7},
    };

    private BackgroundTrack() {
    }

    /**
     * Renders the track and writes it into the game's resources.
     *
     * @param args ignored
     * @throws IOException if the file cannot be written
     */
    public static void main(String[] args) throws IOException {
        int total = (int) (BAR_SECONDS * BARS * RATE);
        double[] mix = new double[total];

        for (int bar = 0; bar < BARS; bar++) {
            int section = bar / 8;
            int[] chord = HARMONY[bar];
            double start = bar * BAR_SECONDS;

            //Three softly voiced chord tones make a warmer bed than a bare root
            //and fifth. The third section thins out to give the ear a rest.
            double sectionLevel = section == 2 ? 0.72 : 1.0;
            pad(mix, start, hertz(chord[0] - 12), 0.23 * sectionLevel);
            pad(mix, start, hertz(chord[2] - 12), 0.13 * sectionLevel);
            pad(mix, start, hertz(chord[1] - 12), 0.075 * sectionLevel);

            //Each long section has its own movement. Alternating bars borrow the
            //next pattern, so even a section does not stamp one bar eight times.
            int[] pattern = ARPEGGIOS[(section + bar % 2) % ARPEGGIOS.length];
            for (int step = 0; step < 8; step++) {
                int degree = pattern[step];
                if (degree < 0) {
                    continue;
                }
                int tone = chord[degree % 4] + degree / 4 * 12;
                //A tiny, deterministic lift on the offbeat keeps the clock from
                //sounding quantised without moving anything outside its bar.
                double offbeat = step % 2 == 1 ? 0.022 : 0;
                double level = (0.18 + (step % 3) * 0.012) * sectionLevel;
                pluck(mix, start + step * BAR_SECONDS / 8 + offbeat,
                        hertz(tone), level);
            }

            addAnswerPhrase(mix, bar, start, chord);
        }

        soften(mix);
        write(mix);

        System.out.printf("Wrote %s (%.1f seconds, %.1f MB)%n",
                TARGET, total / RATE, Files.size(TARGET) / 1048576.0);
    }

    /**
     * A small musical question near the end of three sections. Leaving it out
     * of the sparse third section makes its silence part of the variation.
     */
    private static void addAnswerPhrase(double[] mix, int bar, double start, int[] chord) {
        if (bar != 6 && bar != 14 && bar != 30) {
            return;
        }
        int section = bar / 8;
        int firstDegree = section == 1 ? 1 : 2;
        pluck(mix, start + BAR_SECONDS * 0.57,
                hertz(chord[firstDegree] + 12), 0.105);
        pluck(mix, start + BAR_SECONDS * 0.76,
                hertz(chord[(firstDegree + 1) % 4] + 12), 0.085);
    }

    private static double hertz(int semitonesFromMiddleC) {
        return 261.625565 * Math.pow(2, semitonesFromMiddleC / 12.0);
    }

    /** A sustained chord tone, soft at both ends so it never announces itself. */
    private static void pad(double[] mix, double start, double hertz, double level) {
        double length = BAR_SECONDS + 0.5;
        int samples = (int) (length * RATE);
        for (int i = 0; i < samples; i++) {
            double t = i / RATE;
            double envelope = Math.min(1, t / 0.4) * Math.min(1, (length - t) / 0.6);
            //Two detuned voices, which is what stops it sounding like a test tone.
            double voice = Math.sin(2 * Math.PI * hertz * t)
                    + 0.7 * Math.sin(2 * Math.PI * hertz * 1.003 * t)
                    + 0.25 * Math.sin(2 * Math.PI * hertz * 2 * t);
            add(mix, start, i, voice / 1.95 * envelope * level);
        }
    }

    /** One arpeggio note: quick to speak, slow to fade, overlapping its neighbours. */
    private static void pluck(double[] mix, double start, double hertz, double level) {
        int samples = (int) (1.1 * RATE);
        for (int i = 0; i < samples; i++) {
            double t = i / RATE;
            double envelope = Math.min(1, t / 0.02) * Math.exp(-t / 0.32);
            double voice = Math.sin(2 * Math.PI * hertz * t)
                    + 0.22 * Math.sin(2 * Math.PI * hertz * 2 * t)
                    + 0.08 * Math.sin(2 * Math.PI * hertz * 3 * t);
            add(mix, start, i, voice / 1.3 * envelope * level);
        }
    }

    /**
     * Adds a sample, wrapping past the end back to the beginning.
     *
     * <p>This is what makes the loop seamless: a note still ringing when the
     * track ends carries into the start, so the join has no gap and no click.</p>
     */
    private static void add(double[] mix, double start, int offset, double value) {
        int at = ((int) (start * RATE) + offset) % mix.length;
        mix[at] += value;
    }

    /** Rolls the top off, then sets the peak low: this plays under a game. */
    private static void soften(double[] mix) {
        //Run the filter over the track twice, keeping only the second pass. The
        //first leaves its state where the end of the track puts it, so the
        //second starts from the value the loop will actually arrive at, and the
        //join stays continuous. Filtering once from silence puts a step there.
        double previous = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < mix.length; i++) {
                double filtered = previous + (mix[i] - previous) * 0.55;
                previous = filtered;
                if (pass == 1) {
                    mix[i] = filtered;
                }
            }
        }
        double peak = 0;
        for (double sample : mix) {
            peak = Math.max(peak, Math.abs(sample));
        }
        for (int i = 0; i < mix.length; i++) {
            mix[i] = mix[i] / peak * 0.45;
        }
    }

    private static void write(double[] mix) throws IOException {
        byte[] bytes = new byte[mix.length * 2];
        for (int i = 0; i < mix.length; i++) {
            int value = (int) Math.round(mix[i] * 32767);
            bytes[i * 2] = (byte) (value & 0xFF);
            bytes[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        AudioFormat format = new AudioFormat(RATE, 16, CHANNELS, true, false);
        Files.createDirectories(TARGET.getParent());
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(bytes), format, mix.length)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, new File(TARGET.toString()));
        }
    }
}
