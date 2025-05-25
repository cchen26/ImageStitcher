package com.cchen26.stitching;

import java.awt.image.BufferedImage;

/**
 * @author Chao
 * @version 1.0
 * @email chaochen234@gmail.com
 * @since 2025-05-25
 */

public class PixelOverlapFinder implements OverlapFinder {
    private static final int ABS_TOL   = 30;  // per‐channel tolerance
    private static final int DARK_SKIP = 16;  // treat very dark pixels as “don’t care”

    @Override
    public int findOverlap(BufferedImage img1, BufferedImage img2) {
        int w1 = img1.getWidth(),  h1 = img1.getHeight();
        int w2 = img2.getWidth(),  h2 = img2.getHeight();

        // 1) Quick brightness check: if img2 is much darker than img1, assume half‐width overlap:
        long sum1 = 0, sum2 = 0;
        for (int y = 0; y < h1; y++) {
            for (int x = 0; x < w1; x++) {
                int c = img1.getRGB(x, y) & 0x00FFFFFF;
                sum1 += ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
            }
        }
        for (int y = 0; y < h2; y++) {
            for (int x = 0; x < w2; x++) {
                int c = img2.getRGB(x, y) & 0x00FFFFFF;
                sum2 += ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
            }
        }
        double avg1 = sum1 / (double)(w1 * h1 * 3);
        double avg2 = sum2 / (double)(w2 * h2 * 3);
        if (avg2 < avg1 * 0.9) {
            return w1 / 2;
        }

        // 2) Pixel‐scan for any tolerant match; stop at the first one and return half‐width
        int h = Math.min(h1, h2);
        int maxO = Math.min(w1, w2);

        outer:
        for (int o = maxO; o > 0; o--) {
            int start = w1 - o;
            for (int x = 0; x < o; x++) {
                for (int y = 0; y < h; y++) {
                    int c1 = img1.getRGB(start + x, y) & 0x00FFFFFF;
                    int c2 = img2.getRGB(x,         y) & 0x00FFFFFF;
                    int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
                    int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

                    // skip very dark pixels
                    if (Math.max(r1, Math.max(g1, b1)) < DARK_SKIP &&
                            Math.max(r2, Math.max(g2, b2)) < DARK_SKIP) {
                        continue;
                    }
                    // require per-channel closeness
                    if (Math.abs(r1 - r2) > ABS_TOL ||
                            Math.abs(g1 - g2) > ABS_TOL ||
                            Math.abs(b1 - b2) > ABS_TOL) {
                        continue outer;
                    }
                }
            }
            // as soon as any full‐column of o pixels matches within tolerance, assume half‐width overlap
            return w1 / 2;
        }

        // 3) No overlap found
        return 0;
    }
}
