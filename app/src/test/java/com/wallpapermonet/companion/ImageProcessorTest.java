package com.wallpapermonet.companion;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public class ImageProcessorTest {
    @Test public void outputIsLimitedWithoutUpscaling() {
        assertArrayEquals(new int[]{1080, 2400}, ImageProcessor.outputDimensions(2160, 4800, 1080, 2400));
        assertArrayEquals(new int[]{1080, 2400}, ImageProcessor.outputDimensions(1440, 3200, 1080, 2400));
        assertArrayEquals(new int[]{1080, 2400}, ImageProcessor.outputDimensions(1080, 2400, 1080, 2400));
        assertArrayEquals(new int[]{540, 1200}, ImageProcessor.outputDimensions(540, 1200, 1080, 2400));
    }
    @Test(expected = IllegalArgumentException.class) public void invalidDimensionsAreRejected() {
        ImageProcessor.outputDimensions(Float.NaN, 1200, 1080, 2400);
    }
}
