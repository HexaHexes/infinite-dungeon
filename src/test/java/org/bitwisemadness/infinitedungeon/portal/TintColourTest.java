package org.bitwisemadness.infinitedungeon.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The tint config's hex rule: what is accepted, what falls back to default. */
class TintColourTest {

    @Test
    void wellFormedColoursAreAccepted() {
        assertTrue(TintColour.isValidFormat("FFFFFF"));
        assertTrue(TintColour.isValidFormat("88CCFF"));
        assertTrue(TintColour.isValidFormat("#ff88cc"));
        assertTrue(TintColour.isValidFormat("#88ccff"));
    }

    @Test
    void malformedValuesAreRejected() {
        assertFalse(TintColour.isValidFormat(null));
        assertFalse(TintColour.isValidFormat(""));
        assertFalse(TintColour.isValidFormat("FFF"));
        assertFalse(TintColour.isValidFormat("FFFFF"));
        assertFalse(TintColour.isValidFormat("FFFFFFF"));
        assertFalse(TintColour.isValidFormat("GG0000"));
        assertFalse(TintColour.isValidFormat("88 CCFF"));
        assertFalse(TintColour.isValidFormat("#88CCF"));
    }

    @Test
    void parsingYieldsTheRgbValue() {
        assertEquals(0xFFFFFF, TintColour.parse("FFFFFF").rgb());
        assertEquals(0x88CCFF, TintColour.parse("88CCFF").rgb());
        assertEquals(0x88CCFF, TintColour.parse("#88ccff").rgb());
    }

    @Test
    void malformedParsingFallsBackToTheDefault() {
        assertEquals(TintColour.DEFAULT.rgb(), TintColour.parse("not a colour").rgb());
        assertEquals(TintColour.DEFAULT.rgb(), TintColour.parse(null).rgb());
        // The shipped default leaves the vanilla texture untouched.
        assertEquals(0xFFFFFF, TintColour.DEFAULT.rgb());
    }

    @Test
    void formatRoundTrips() {
        assertEquals("88CCFF", TintColour.format(TintColour.parse("#88ccff")));
        assertEquals("FFFFFF", TintColour.format(TintColour.DEFAULT));
        assertTrue(TintColour.isValidFormat(TintColour.format(new TintColour(0x123456))));
    }
}
