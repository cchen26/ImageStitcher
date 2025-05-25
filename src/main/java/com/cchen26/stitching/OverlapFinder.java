package com.cchen26.stitching;

import java.awt.image.BufferedImage;

/**
 * @author Chao
 * @version 1.0
 * @email chaochen234@gmail.com
 * @since 2025-05-25
 */

public interface OverlapFinder {
    int findOverlap(BufferedImage image1, BufferedImage image2);
}
