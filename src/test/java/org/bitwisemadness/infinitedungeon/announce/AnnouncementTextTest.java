package org.bitwisemadness.infinitedungeon.announce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The announcement wording rules: token rendering, and the house rule that
 * shipped texts stay ASCII-only with no emojis.
 */
class AnnouncementTextTest {

    @Test
    void tokensAreReplaced() {
        assertEquals("You reached layer 3, floor 7.",
                AnnouncementText.render("You reached layer {layer}, floor {floor}.",
                        Map.of("layer", "3", "floor", "7")));
    }

    @Test
    void unknownTokensStayAsWritten() {
        assertEquals("Layer {layer} awaits.",
                AnnouncementText.render("Layer {layer} awaits.", Map.of("floor", "7")));
    }

    @Test
    void nullTemplatesRenderEmpty() {
        assertEquals("", AnnouncementText.render(null, Map.of("layer", "3")));
    }

    @Test
    void shippedTextsAreAsciiOnly() {
        for (String text : new String[] {
                AnnouncementTexts.ADVENTURE_LOCK,
                AnnouncementTexts.ANCHOR_FALLBACK,
                AnnouncementTexts.BOSS_AWAKENING,
                AnnouncementTexts.ROOM_EVENT,
                AnnouncementTexts.PORTAL_NOT_ALLOWED}) {
            for (int i = 0; i < text.length(); i++) {
                assertTrue(text.charAt(i) < 128,
                        "announcement texts must stay ASCII-only: " + text);
            }
        }
    }
}
