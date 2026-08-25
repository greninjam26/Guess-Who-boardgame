import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlayerTest {
    @Test
    void rejectsUnknownCharacterNames() throws Exception {
        Player player = new Player("");

        assertThrows(IllegalArgumentException.class,
                () -> player.findCharacter("Unknown character"));
    }
}
