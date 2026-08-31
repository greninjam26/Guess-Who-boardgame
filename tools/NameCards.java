import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Adds each character's name to their portrait, the way the printed cards do.
 *
 * <p>The names come from {@code GuessWhoDB.csv} rather than from the artwork.
 * An image generator asked for lettering misspells it often enough to matter
 * over twenty-four cards, and varies the type on every one; drawing the name
 * here spells it the way the game does and puts it in the same place every
 * time. Renaming a character then costs a re-run rather than new art.</p>
 *
 * <p>Reads the generated portraits from {@code tools/portraits} and writes
 * finished cards to the game's resources, so it can be run again without
 * stamping a name onto a name.</p>
 *
 * <pre>java tools/NameCards.java</pre>
 */
public final class NameCards {
    /** Six times the 100x150 the board draws, so HiDPI has detail to spare. */
    private static final int WIDTH = 600;
    private static final int HEIGHT = 900;
    /** The name sits in the bottom sixth, which the prompts keep clear. */
    private static final int BAND = HEIGHT / 6;

    private static final Path DATA =
            Path.of("game-core/src/main/resources/data/GuessWhoDB.csv");
    private static final Path SOURCE = Path.of("tools/portraits");
    /** Whatever the generator happened to save; the finished cards are always JPEG. */
    private static final String[] SOURCE_TYPES = {".png", ".jpg", ".jpeg", ".gif", ".bmp"};
    private static final Path TARGET = Path.of("game-core/src/main/resources/images");

    private NameCards() {
    }

    /**
     * Stamps every portrait that has been generated so far.
     *
     * @param args ignored
     * @throws IOException if the data or an image cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        List<String> names = new ArrayList<>();
        for (String line : Files.readAllLines(DATA, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                names.add(line.split(",")[0].trim());
            }
        }
        Files.createDirectories(TARGET);

        List<String> missing = new ArrayList<>();
        int done = 0;
        for (int index = 0; index < names.size(); index++) {
            Path portrait = portraitFor(index);
            if (portrait == null) {
                missing.add(index + " (" + names.get(index) + ")");
                continue;
            }
            BufferedImage card = stamp(ImageIO.read(portrait.toFile()), names.get(index));
            write(card, TARGET.resolve(index + ".jpg"));
            done++;
        }

        System.out.println("Stamped " + done + " of " + names.size() + " cards into " + TARGET);
        if (!missing.isEmpty()) {
            System.out.println("Still to generate: " + String.join(", ", missing));
        }
    }

    /**
     * Finds a character's portrait whatever the generator saved it as.
     *
     * @param index board position of the character
     * @return the file, or null when that one has not been generated yet
     */
    private static Path portraitFor(int index) {
        for (String type : SOURCE_TYPES) {
            Path candidate = SOURCE.resolve(index + type);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Crops to the board's 2:3, scales, then letters the name across the foot. */
    private static BufferedImage stamp(BufferedImage source, String name) {
        BufferedImage card = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = card.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawCropped(g, trimmed(source));
        band(g, name);

        g.dispose();
        return card;
    }

    /**
     * Drops any band of flat background the generator left below the character.
     *
     * <p>Asking for clear space at the foot of the portrait gets it, but as a
     * hard edge partway up the card: the shoulders stop and the background
     * runs on. Trimming back to the last row that still has the character in
     * it puts that edge under the name band, where it belongs.</p>
     *
     * @param source the portrait as generated
     * @return the portrait without its empty foot, or unchanged if it has none
     */
    private static BufferedImage trimmed(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int background = source.getRGB(2, height - 2);
        int lowest = height - 1;
        while (lowest > height / 2 && uniform(source, lowest, background, width)) {
            lowest--;
        }
        //A portrait that already reaches the foot of the frame needs nothing,
        //and one that looks blank is more likely being misread than empty.
        if (lowest >= height - 3 || lowest < height * 3 / 5) {
            return source;
        }
        return source.getSubimage(0, 0, width, lowest + 1);
    }

    /** True when a row is all one colour, which is what the empty foot looks like. */
    private static boolean uniform(BufferedImage source, int row, int colour, int width) {
        for (int x = 0; x < width; x += 2) {
            if (!close(source.getRGB(x, row), colour)) {
                return false;
            }
        }
        return true;
    }

    /** JPEG and the generator both wander a little, so exact equality is too strict. */
    private static boolean close(int left, int right) {
        return Math.abs(((left >> 16) & 0xFF) - ((right >> 16) & 0xFF)) < 12
                && Math.abs(((left >> 8) & 0xFF) - ((right >> 8) & 0xFF)) < 12
                && Math.abs((left & 0xFF) - (right & 0xFF)) < 12;
    }

    /** Centre-cropped to 2:3, so a portrait of any shape fills the card without distortion. */
    private static void drawCropped(Graphics2D g, BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int wanted = width * HEIGHT / WIDTH;
        int x = 0;
        int y = 0;
        if (height >= wanted) {
            //Taller than the card: trim from the bottom, where there is only
            //background, rather than from the top, which holds hats and hair.
            height = wanted;
        }
        else {
            int trimmed = height * WIDTH / HEIGHT;
            x = (width - trimmed) / 2;
            width = trimmed;
        }
        g.drawImage(source, 0, 0, WIDTH, HEIGHT, x, y, x + width, y + height, null);
    }

    private static void band(Graphics2D g, String name) {
        int top = HEIGHT - BAND;
        g.setColor(new Color(0xF6F1E6));
        g.fill(new Rectangle2D.Double(0, top, WIDTH, BAND));
        g.setColor(new Color(0x9C8F79));
        g.setStroke(new BasicStroke(3));
        g.draw(new Line2D.Double(0, top, WIDTH, top));

        g.setColor(new Color(0x2B2723));
        g.setFont(fitted(g, name));
        int textWidth = g.getFontMetrics().stringWidth(name);
        int baseline = top + (BAND + g.getFontMetrics().getAscent()
                - g.getFontMetrics().getDescent()) / 2;
        g.drawString(name, (WIDTH - textWidth) / 2, baseline);
    }

    /** Shrinks the type for a long name rather than letting it run off the card. */
    private static Font fitted(Graphics2D g, String name) {
        for (int size = 82; size > 30; size -= 2) {
            Font candidate = new Font(Font.SANS_SERIF, Font.BOLD, size);
            if (g.getFontMetrics(candidate).stringWidth(name) <= WIDTH - 80) {
                return candidate;
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 30);
    }

    private static void write(BufferedImage image, Path target) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0.92f);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(new File(target.toString()))) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), params);
        }
        finally {
            writer.dispose();
        }
    }
}
