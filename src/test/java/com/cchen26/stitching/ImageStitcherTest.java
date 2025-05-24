package com.cchen26.stitching;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Chao
 * @version 1.0
 * @email chaochen234@gmail.com
 * @since 2025-05-24
 */

public class ImageStitcherTest {

    @Test
    void testStitchSingleImage() throws StitchingException {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
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
        BufferedImage image1 = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        image1.setRGB(0, 0, 0xFF0000);
        BufferedImage image2 = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
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
        BufferedImage image1 = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 4; x ++){
            for (int y = 0; y < 2; y ++){
                int color = (x < 2) ? 0xFF0000 : 0x0000FF;
                image1.setRGB(x, y, color);
            }
        }
        BufferedImage image2 = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 4; x ++){
            for (int y = 0; y < 2; y ++){
                int color = (x < 2) ? 0x0000FF : 0x00FF00;
                image2.setRGB(x, y, color);
            }
        }

        ImageStitcher stitcher = new ImageStitcher();
        BufferedImage result = stitcher.stitch(Arrays.asList(image1, image2));

        assertEquals(6, result.getWidth(), "Width should account for overlap");
        assertEquals(2, result.getHeight(), "Height should match max height");

        assertEquals(0xFF0000, result.getRGB(0, 0));
        assertEquals(0xFF0000, result.getRGB(1, 1));

        assertEquals(0x0000FF, result.getRGB(2, 0));
        assertEquals(0x0000FF, result.getRGB(3, 1));

        assertEquals(0x00FF00, result.getRGB(4, 0));
        assertEquals(0x00FF00, result.getRGB(5, 1));
    }

}
