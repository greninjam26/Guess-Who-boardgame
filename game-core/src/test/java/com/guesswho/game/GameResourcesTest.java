package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
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
