package com.wallpapermonet.companion;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression fixtures for the Material 2021 palette and Android resource ordering. */
public class MonetEngineTest {
    @Test public void allSupportedStylesProduceFiveOpaquePalettesInResourceOrder() {
        assertArrayEquals(new int[]{0,10,50,100,200,300,400,500,600,700,800,900,1000}, MonetEngine.INDICES);
        for (String style : MonetEngine.STYLES) {
            int[][] palette = MonetEngine.palette(0xff4285f4, style);
            assertEquals(style, 5, palette.length);
            for (int[] group : palette) {
                assertEquals(style, 13, group.length);
                assertEquals(style, 0xffffffff, group[0]);
                assertEquals(style, 0xff000000, group[12]);
                for (int color : group) assertEquals(style, 0xff, color >>> 24);
            }
        }
    }

    @Test public void knownBlueSeedHasExpectedStyleSpecificTone60() {
        assertEquals(0xff7790c7, MonetEngine.palette(0xff4285f4, "TONAL_SPOT")[0][6]);
        assertEquals(0xff4c8eff, MonetEngine.palette(0xff4285f4, "VIBRANT")[0][6]);
        assertEquals(0xff679e63, MonetEngine.palette(0xff4285f4, "EXPRESSIVE")[0][6]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownStyle() {
        MonetEngine.palette(0xff4285f4, "unknown");
    }
}
