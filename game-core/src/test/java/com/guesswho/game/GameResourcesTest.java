package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import org.junit.jupiter.api.Test;

class GameResourcesTest {
    @Test
    void opensPackagedQuestionData() throws Exception {
        try (InputStream input = GameResources.openQuestionData()) {
            assertTrue(input.read() != -1, "Question data should not be empty");
        }
    }

    @Test
    void opensPackagedCharacterData() throws Exception {
        try (InputStream input = GameResources.openCharacterData()) {
            assertTrue(input.read() != -1, "Character data should not be empty");
        }
    }

    @Test
    void loadsAndScalesCharacterImage() {
        ImageIcon icon = GameResources.loadCharacterIcon(0, 100, 150);

        assertEquals(100, icon.getIconWidth());
        assertEquals(150, icon.getIconHeight());
    }

    @Test
    void loadsAndScalesEliminatedCharacterImage() {
        ImageIcon icon = GameResources.loadEliminatedCharacterIcon(100, 150);

        assertEquals(100, icon.getIconWidth());
        assertEquals(150, icon.getIconHeight());
    }

    @Test
    void handlesBackgroundMusicWithoutCrashing() {
        assertDoesNotThrow(GameResources::loadBackgroundMusic);
    }

    @Test
    void backgroundMusicIsLongEnoughNotToAdvertiseItsLoop() throws Exception {
        URL music = GameResources.class.getResource("/audio/Guessing Game.wav");

        try (AudioInputStream stream = AudioSystem.getAudioInputStream(music)) {
            double seconds = stream.getFrameLength() / stream.getFormat().getFrameRate();

            assertTrue(seconds >= 70,
                    "A background loop should run for at least 70 seconds, but was " + seconds);
        }
    }

    @Test
    void backgroundMusicHasDistinctLongSectionsInsteadOfRepeatingOneBlock() throws Exception {
        URL music = GameResources.class.getResource("/audio/Guessing Game.wav");

        try (AudioInputStream stream = AudioSystem.getAudioInputStream(music)) {
            byte[] audio = stream.readAllBytes();
            int samples = audio.length / 2;
            int sectionSamples = samples / 4;

            for (int section = 1; section < 4; section++) {
                long totalDifference = 0;
                for (int sample = 0; sample < sectionSamples; sample++) {
                    totalDifference += Math.abs(
                            sample(audio, sample)
                                    - sample(audio, section * sectionSamples + sample));
                }
                double meanDifference = (double) totalDifference / sectionSamples;
                assertTrue(meanDifference > 400,
                        "Section " + (section + 1)
                                + " is too close to the opening section: " + meanDifference);
            }
        }
    }

    @Test
    void backgroundMusicLeavesHeadroomForGameSounds() throws Exception {
        URL music = GameResources.class.getResource("/audio/Guessing Game.wav");

        try (AudioInputStream stream = AudioSystem.getAudioInputStream(music)) {
            byte[] audio = stream.readAllBytes();
            int peak = 0;
            for (int index = 0; index < audio.length / 2; index++) {
                peak = Math.max(peak, Math.abs(sample(audio, index)));
            }

            assertTrue(peak <= 16_384,
                    "Background music should peak at or below half scale, but reached " + peak);
        }
    }

    private static int sample(byte[] audio, int index) {
        int offset = index * 2;
        return (short) (((audio[offset + 1] & 0xFF) << 8) | (audio[offset] & 0xFF));
    }

    @Test
    void portraitsCarryAHighResolutionCopyForRetinaScreens() {
        ImageIcon icon = GameResources.loadCharacterIcon(0, 100, 150);

        assertEquals(100, icon.getIconWidth(), "The card still occupies its own size");
        assertEquals(150, icon.getIconHeight());
        assertInstanceOf(BaseMultiResolutionImage.class, icon.getImage());
        assertEquals(2, ((BaseMultiResolutionImage) icon.getImage())
                .getResolutionVariants().size(),
                "One copy per scale, so a doubled display has pixels to draw with");
    }

    @Test
    void theSecondCopyIsTwiceTheSize() {
        BaseMultiResolutionImage image =
                (BaseMultiResolutionImage) GameResources.loadCharacterIcon(0, 100, 150).getImage();

        Image doubled = image.getResolutionVariants().get(1);

        assertEquals(200, doubled.getWidth(null));
        assertEquals(300, doubled.getHeight(null));
    }

    @Test
    void theEliminatedCardIsTreatedTheSameWay() {
        assertInstanceOf(BaseMultiResolutionImage.class,
                GameResources.loadEliminatedCharacterIcon(100, 150).getImage());
    }
}
