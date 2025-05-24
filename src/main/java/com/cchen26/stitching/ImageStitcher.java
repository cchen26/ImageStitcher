package com.cchen26.stitching;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * @author Chao
 * @version 1.0
 * @email chaochen234@gmail.com
 * @since 2025-05-24
 */

public class ImageStitcher {

    public BufferedImage stitch(List<BufferedImage> inputs) throws StitchingException {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Input list cannot be null or empty");
        }
        if (inputs.size() == 1) {
            return inputs.getFirst();
        }
        int totalWidth = inputs.stream().mapToInt(BufferedImage::getWidth).sum();
        int maxHeight = inputs.stream().mapToInt(BufferedImage::getHeight).max().orElse(0);

        int imageType = inputs.getFirst().getType();
        BufferedImage panorama = new BufferedImage(totalWidth, maxHeight, imageType);

        int xOffset = 0;
        for (BufferedImage image : inputs) {
            int w = image.getWidth(), h = image.getHeight();
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    panorama.setRGB(xOffset + x, y, image.getRGB(x, y));
                }
            }
            xOffset += w;
        }
        return panorama;
    }

}
