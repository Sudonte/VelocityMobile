package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IdImageCompressorTest {

    @Test
    public void smallImagesAreNotSubsampled() {
        assertEquals(1, IdImageCompressor.sampleSizeFor(1200, 800, 1600));
        assertEquals(1, IdImageCompressor.sampleSizeFor(1600, 1200, 1600));
    }

    @Test
    public void largeCameraPhotosStayAtOrAboveTheCap() {
        int[][] sizes = {{4000, 3000}, {8000, 6000}, {12000, 9000}, {3000, 4000}};
        for (int[] size : sizes) {
            int sample = IdImageCompressor.sampleSizeFor(size[0], size[1], 1600);
            assertTrue(sample >= 1);
            assertTrue(Math.max(size[0], size[1]) / sample >= 1600);
            // and the next power of two would have dropped below the cap
            assertTrue(Math.max(size[0], size[1]) / (sample * 2) < 1600);
        }
        assertEquals(2, IdImageCompressor.sampleSizeFor(4000, 3000, 1600));
    }
}
