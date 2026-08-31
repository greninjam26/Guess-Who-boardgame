import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Draws the application icon at every size the installers need.
 *
 * <p>One drawing, rendered rather than scaled. An icon shrunk from a single
 * large picture goes muddy at sixteen pixels, which is the size it is seen at
 * most often — in a dock, a task bar, a title bar. Drawing it again at each
 * size keeps the proportions doing what they were meant to.</p>
 *
 * <p>The mark is a card with a question mark, in the colour of the face-down
 * cards on the board. A single bold shape survives the small sizes; the grid
 * of twenty-four faces this game is actually about would be mush.</p>
 *
 * <pre>java tools/ApplicationIcon.java</pre>
 *
 * <p>Writes a Windows {@code .ico} and a macOS {@code .iconset}, the latter
 * turned into an {@code .icns} by {@code iconutil}, which only exists on
 * macOS.</p>
 */
public final class ApplicationIcon {
    private static final Path TARGET = Path.of("packaging");

    /** The face-down card's colour, so the icon belongs to the same game. */
    private static final Color GROUND = new Color(0x2F4858);
    private static final Color CARD = new Color(0xF6F1E6);
    private static final Color BAND = new Color(0xD9CFBA);
    private static final Color MARK = new Color(0x2F4858);

    /** Windows reads these; 256 is written as zero in the header. */
    private static final int[] WINDOWS_SIZES = {16, 20, 24, 32, 48, 64, 128, 256};

    private ApplicationIcon() {
    }

    /**
     * Renders every size and writes both icon files.
     *
     * @param args ignored
     * @throws IOException if a file cannot be written
     */
    public static void main(String[] args) throws IOException {
        Files.createDirectories(TARGET);
        Path iconset = TARGET.resolve("GuessWho.iconset");
        Files.createDirectories(iconset);

        //macOS asks for each size twice over, once per pixel density.
        Map<String, Integer> macSizes = new LinkedHashMap<>();
        macSizes.put("icon_16x16.png", 16);
        macSizes.put("icon_16x16@2x.png", 32);
        macSizes.put("icon_32x32.png", 32);
        macSizes.put("icon_32x32@2x.png", 64);
        macSizes.put("icon_128x128.png", 128);
        macSizes.put("icon_128x128@2x.png", 256);
        macSizes.put("icon_256x256.png", 256);
        macSizes.put("icon_256x256@2x.png", 512);
        macSizes.put("icon_512x512.png", 512);
        macSizes.put("icon_512x512@2x.png", 1024);
        for (Map.Entry<String, Integer> size : macSizes.entrySet()) {
            ImageIO.write(icon(size.getValue()), "png",
                    iconset.resolve(size.getKey()).toFile());
        }

        writeWindowsIcon(TARGET.resolve("GuessWho.ico"));
        //Useful on its own, for a README or a page about the project.
        ImageIO.write(icon(512), "png", TARGET.resolve("icon.png").toFile());

        System.out.println("Wrote " + iconset + " and " + TARGET.resolve("GuessWho.ico"));
        System.out.println("Now run: iconutil --convert icns \"" + iconset + "\""
                + " --output \"" + TARGET.resolve("GuessWho.icns") + "\"");
    }

    /** Draws the mark at one size, in that size's own proportions. */
    private static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double scale = size / 1024.0;
        //The rounded square macOS expects an application to be drawn inside.
        g.setColor(GROUND);
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, 225 * scale, 225 * scale));

        double cardWidth = 520 * scale;
        double cardHeight = 700 * scale;
        double cardX = (size - cardWidth) / 2;
        double cardY = (size - cardHeight) / 2;
        double radius = Math.max(2 * scale, 60 * scale);

        g.setColor(CARD);
        g.fill(new RoundRectangle2D.Double(cardX, cardY, cardWidth, cardHeight,
                radius, radius));

        //The name band from the character cards, kept above a pixel so it does
        //not vanish into the card edge at the smallest sizes.
        double bandHeight = Math.max(1, cardHeight / 6);
        g.setColor(BAND);
        g.fill(new Rectangle2D.Double(cardX, cardY + cardHeight - bandHeight,
                cardWidth, bandHeight - radius / 4));
        g.setColor(CARD);
        g.fill(new RoundRectangle2D.Double(cardX, cardY + cardHeight - radius,
                cardWidth, radius, radius, radius));
        g.setColor(BAND);
        g.fill(new Rectangle2D.Double(cardX, cardY + cardHeight - bandHeight,
                cardWidth, Math.max(1, 8 * scale)));

        drawQuestionMark(g, size, cardX, cardY, cardWidth, cardHeight - bandHeight);

        g.dispose();
        return image;
    }

    /** Centred by its own outline rather than its font metrics, which lie. */
    private static void drawQuestionMark(Graphics2D g, int size,
            double x, double y, double width, double height) {
        g.setColor(MARK);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(6, height * 0.86));
        g.setFont(font);
        var bounds = font.createGlyphVector(g.getFontRenderContext(), "?")
                .getVisualBounds();
        double markX = x + (width - bounds.getWidth()) / 2 - bounds.getX();
        double markY = y + (height - bounds.getHeight()) / 2 - bounds.getY();
        g.drawString("?", (float) markX, (float) markY);
    }

    /**
     * Writes a Windows icon: a small header, then one PNG per size.
     *
     * <p>Written by hand because {@code ImageIO} has no ICO writer. The format
     * is a directory of images, and every size Windows might ask for is
     * included so it never has to scale one itself.</p>
     */
    private static void writeWindowsIcon(Path target) throws IOException {
        byte[][] images = new byte[WINDOWS_SIZES.length][];
        for (int i = 0; i < WINDOWS_SIZES.length; i++) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(icon(WINDOWS_SIZES[i]), "png", png);
            images[i] = png.toByteArray();
        }

        try (OutputStream out = Files.newOutputStream(target)) {
            writeShort(out, 0);                     //reserved
            writeShort(out, 1);                     //1 means icon, not cursor
            writeShort(out, images.length);

            int offset = 6 + 16 * images.length;
            for (int i = 0; i < images.length; i++) {
                //256 does not fit in a byte, and zero is how the format says it.
                out.write(WINDOWS_SIZES[i] == 256 ? 0 : WINDOWS_SIZES[i]);
                out.write(WINDOWS_SIZES[i] == 256 ? 0 : WINDOWS_SIZES[i]);
                out.write(0);                       //not a palette
                out.write(0);                       //reserved
                writeShort(out, 1);                 //colour planes
                writeShort(out, 32);                //bits per pixel
                writeInt(out, images[i].length);
                writeInt(out, offset);
                offset += images[i].length;
            }
            for (byte[] image : images) {
                out.write(image);
            }
        }
    }

    private static void writeShort(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
        writeShort(out, value & 0xFFFF);
        writeShort(out, (value >> 16) & 0xFFFF);
    }
}
