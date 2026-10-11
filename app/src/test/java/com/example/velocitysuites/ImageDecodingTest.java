package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ImageDecodingTest {

    @Test
    public void bigPhotoIsSampledDownButNeverBelowTheTarget() {
        assertEquals(2, ImageDecoding.sampleSizeFor(4000, 3000, 1280)); // 2000x1500 still >= 1280
        assertEquals(1, ImageDecoding.sampleSizeFor(1600, 1200, 1280)); // halving would drop under 1280 on one edge
        assertEquals(4, ImageDecoding.sampleSizeFor(8000, 6000, 1280));
    }

    @Test
    public void unknownOrSmallSizesAreLeftAlone() {
        assertEquals(1, ImageDecoding.sampleSizeFor(0, 0, 1280));
        assertEquals(1, ImageDecoding.sampleSizeFor(640, 480, 1280));
        assertEquals(1, ImageDecoding.sampleSizeFor(4000, 3000, 0));
    }
}
