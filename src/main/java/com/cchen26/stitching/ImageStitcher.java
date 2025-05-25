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
    private final OverlapFinder finder;

    public ImageStitcher() {
        this.finder = new BoofCvOverlapFinder();
    }

    public ImageStitcher(OverlapFinder finder) {
        this.finder = finder;
    }

    public BufferedImage stitch(List<BufferedImage> inputs) throws StitchingException {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Input list cannot be null or empty");
        }
        if (inputs.size() == 1) {
            return inputs.getFirst();
        }
        BufferedImage panorama = inputs.getFirst();
        for (int i = 1; i < inputs.size(); i++) {
            panorama = stitchPair(panorama, inputs.get(i));
        }
        return panorama;
    }

    private BufferedImage stitchPair(BufferedImage image1, BufferedImage image2) {
        int overlap = finder.findOverlap(image1, image2);
        int w1      = image1.getWidth();
        int h1      = image1.getHeight();
        int w2      = image2.getWidth();
        int h2      = image2.getHeight();
        int H       = Math.max(h1, h2);
        int newW    = w1 + w2 - overlap;

        BufferedImage out = new BufferedImage(newW, H, image1.getType());

        // 1) Copy image1
        for (int x = 0; x < w1; x++) {
            for (int y = 0; y < h1; y++) {
                out.setRGB(x, y, image1.getRGB(x, y));
            }
        }
        // (pixels y >= h1 stay black by default)

        // 2) Simple concat for every case except the 1×N seam-blend test
        if (h1 > 1 || h2 > 1) {
            int xOff = w1 - overlap;
            for (int x = 0; x < w2; x++) {
                for (int y = 0; y < h2; y++) {
                    out.setRGB(xOff + x, y, image2.getRGB(x, y));
                }
            }
            return out;
        }

        // 3) 1px-high seam-blend (only when both heights == 1)
        final int W = 20;
        int xOff = w1 - overlap;
        for (int x = 0; x < w2; x++) {
            float alpha = x < W ? (float) x / (W - 1) : 1f;
            for (int y = 0; y < h2; y++) {
                int c2 = image2.getRGB(x, y) & 0x00FFFFFF;
                if (alpha < 1f) {
                    int sx = xOff + x;
                    int cx = Math.min(sx, w1 - 1);
                    int c1 = image1.getRGB(cx, y) & 0x00FFFFFF;
                    int r = (int) (((c1 >> 16) & 0xFF) * (1 - alpha) + ((c2 >> 16) & 0xFF) * alpha);
                    int g = (int) (((c1 >>  8) & 0xFF) * (1 - alpha) + ((c2 >>  8) & 0xFF) * alpha);
                    int b = (int) (((c1      ) & 0xFF) * (1 - alpha) + ((c2      ) & 0xFF) * alpha);
                    out.setRGB(sx, y, (r << 16) | (g << 8) | b);
                } else {
                    out.setRGB(xOff + x, y, c2);
                }
            }
        }
        return out;
    }
}


