package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import javax.swing.ImageIcon;
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
}
