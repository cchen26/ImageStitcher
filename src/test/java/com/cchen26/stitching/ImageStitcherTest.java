package com.cchen26.stitching;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Chao
 * @version 1.0
 * @email chaochen234@gmail.com
 * @since 2025-05-24
 */

public class ImageStitcherTest {

    private BufferedImage createGradientImage(int w, int h, int startColor, int endColor, boolean horizontal) {
        BufferedImage img = new BufferedImage(w, h, TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float ratio = horizontal ? (float)x/w : (float)y/h;
                int r = (int) ((startColor >> 16 & 0xFF) * (1 - ratio) + (endColor >> 16 & 0xFF) * ratio);
                int g = (int) ((startColor >> 8 & 0xFF) * (1 - ratio) + (endColor >> 8 & 0xFF) * ratio);
                int b = (int) ((startColor & 0xFF) * (1 - ratio) + (endColor & 0xFF) * ratio);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    @Test
    void testStitchSingleImage() throws StitchingException {
        BufferedImage image = new BufferedImage(100, 100, TYPE_INT_RGB);
        ImageStitcher stitcher = new ImageStitcher();
        BufferedImage result = stitcher.stitch(Collections.singletonList(image));
        assertSame(image, result, "Stitching a single image should return the same instance");
    }

    @Test
    void testStitchEmptyListThrows() {
        ImageStitcher stitcher = new ImageStitcher();
        assertThrows(IllegalArgumentException.class, () -> stitcher.stitch(Collections.emptyList()));
    }

    @Test
    void testStitchTwoImagesSimpleConcatenate() throws StitchingException {
        BufferedImage image1 = new BufferedImage(2, 3, TYPE_INT_RGB);
        image1.setRGB(0, 0, 0xFF0000);
        BufferedImage image2 = new BufferedImage(2, 3, TYPE_INT_RGB);
        image2.setRGB(0, 0, 0x00FF00);

        ImageStitcher stitcher = new ImageStitcher();
        BufferedImage result = stitcher.stitch(Arrays.asList(image1, image2));

        assertEquals(4, result.getWidth(), "Width should be sum of both widths");
        assertEquals(3, result.getHeight(), "Height should be max of both heights");
        assertEquals(image1.getRGB(0,0), result.getRGB(0,0));
        assertEquals(image2.getRGB(0,0), result.getRGB(2,0));
    }

    @Test
    void testStitchTwoImagesWithOverlap() throws StitchingException {
        // Create 100x100 images with clear overlap pattern
        BufferedImage left = createGradientImage(100, 100, 0xFF0000, 0x00FF00, true);
        BufferedImage right = createGradientImage(100, 100, 0x00FF00, 0x0000FF, true);

        ImageStitcher stitcher = new ImageStitcher();
        BufferedImage result = stitcher.stitch(Arrays.asList(left, right));

        // Expect about 150px width (100 + 100 - 50px overlap)
        assertTrue(result.getWidth() > 140 && result.getWidth() < 160,
                "Width should reflect partial overlap");
    }

    @Test
    void testStitchDifferentHeights() throws StitchingException {
        BufferedImage img1 = new BufferedImage(2, 2, TYPE_INT_RGB);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                img1.setRGB(x, y, 0xFF0000);
            }
        }

        BufferedImage img2 = new BufferedImage(2, 3, TYPE_INT_RGB);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 3; y++) {
                img2.setRGB(x, y, 0x00FF00);
            }
        }

        ImageStitcher stitcher = new ImageStitcher();
        BufferedImage result = stitcher.stitch(Arrays.asList(img1, img2));

        assertEquals(4, result.getWidth(), "Width should be sum of widths when no overlap");
        assertEquals(3, result.getHeight(), "Height should be max of both heights");

        assertEquals(0x00FF00, result.getRGB(2, 2) & 0x00FFFFFF,
                "Pixels in the extra row of second image should be green");
        assertEquals(0, result.getRGB(0, 2) & 0x00FFFFFF,
                "Pixels above first image's height should be default black");

    }

    @Test
    void testStitchWithBrightnessVariation() throws StitchingException {
        // Use larger images for better feature detection
        BufferedImage base = createGradientImage(200, 200, 0xFFFFFF, 0x000000, true);
        BufferedImage darkened = new BufferedImage(200, 200, TYPE_INT_RGB);

        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 200; y++) {
                int rgb = base.getRGB(x, y);
                int r = (int) ((rgb >> 16 & 0xFF) * 0.7);
                int g = (int) ((rgb >> 8 & 0xFF) * 0.7);
                int b = (int) ((rgb & 0xFF) * 0.7);
                darkened.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        ImageStitcher s = new ImageStitcher();
        BufferedImage result = s.stitch(Arrays.asList(base, darkened));

        // Expect base width + partial overlap
        assertTrue(result.getWidth() > 250 && result.getWidth() < 350);
    }

    @Test
    void testSeamBlendingLinearAlpha() throws StitchingException {
        // Blend width W in your stitchPair (must match the code; default was 20)
        final int W = 20;

        // Build two 1-pixel-high images: left=solid red, right=solid blue
        int w1 = 30, w2 = 30;
        BufferedImage img1 = new BufferedImage(w1, 1, TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(w2, 1, TYPE_INT_RGB);
        for (int x = 0; x < w1; x++) img1.setRGB(x, 0, 0xFF0000);
        for (int x = 0; x < w2; x++) img2.setRGB(x, 0, 0x0000FF);

        ImageStitcher stitcher = new ImageStitcher();
        BufferedImage result = stitcher.stitch(Arrays.asList(img1, img2));

        // If no overlap was detected, xOffset = w1; otherwise w1 - overlap.
        // Since both images are distinct, overlap==0, so:
        int xOffset = w1;

        // 1) At the very start of img2’s region (xOffset), alpha=0 → should be pure img1 (red)
        assertEquals(0xFF0000, result.getRGB(xOffset, 0) & 0x00FFFFFF,
                "At blend start α=0 → pure left image (red)");

        // 2) At the end of the blend window (xOffset + W - 1), α=1 → pure img2 (blue)
        assertEquals(0x0000FF, result.getRGB(xOffset + W - 1, 0) & 0x00FFFFFF,
                "At blend end α=1 → pure right image (blue)");

        // 3) In the middle of the window (xOffset + W/2), α≈0.5 → purple (~127,0,127)
        int xMid = xOffset + W / 2;
        int mixed = result.getRGB(xMid, 0) & 0x00FFFFFF;
        int r = (mixed >> 16) & 0xFF;
        int g = (mixed >> 8) & 0xFF;
        int b = (mixed >>  0) & 0xFF;
        assertTrue(Math.abs(r - 127) <= 10 && g == 0 && Math.abs(b - 127) <= 10,
                String.format("At blend mid α≈0.5 → purple-ish, got R=%d G=%d B=%d", r, g, b));
    }


}
