package com.cchen26.stitching;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.enhance.EnhanceImageOps;
import boofcv.core.image.GConvertImage;
import boofcv.factory.feature.associate.ConfigAssociateGreedy;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import georegression.geometry.GeometryMath_F64;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.FastAccess;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Chao
 * @version 1.0
 * @email chaochen234@gmail.com
 * @since 2025-05-25
 */

public class BoofCvOverlapFinder implements OverlapFinder {
    private final OverlapFinder fallback = new PixelOverlapFinder();

    @Override
    public int findOverlap(BufferedImage image1, BufferedImage image2) {

        // 1) convert to grayscale
        GrayU8 u1 = ConvertBufferedImage.convertFrom(image1, (GrayU8) null);
        GrayU8 u2 = ConvertBufferedImage.convertFrom(image2, (GrayU8) null);

        // 2) local equalization
        EnhanceImageOps.equalizeLocal(u1, 20, u1, 256, null);
        EnhanceImageOps.equalizeLocal(u2, 20, u2, 256, null);

        // 3) convert to GrayF32 for SURF
        GrayF32 gray1 = new GrayF32(u1.width, u1.height);
        GrayF32 gray2 = new GrayF32(u2.width, u2.height);
        GConvertImage.convert(u1, gray1);
        GConvertImage.convert(u2, gray2);

        DetectDescribePoint<GrayF32, TupleDesc_F64> detDesc =
                FactoryDetectDescribe.surfStable(
                        new ConfigFastHessian(0, 2, 200, 1, 9, 4, 4), // Lower detectThreshold, more features
                        null,
                        null,
                        GrayF32.class
                );

        // extract features from img1
        List<Point2D_F64> pts1 = new ArrayList<>();
        List<TupleDesc_F64> ds1 = new ArrayList<>();
        detDesc.detect(gray1);
        for (int i = 0; i < detDesc.getNumberOfFeatures(); i++) {
            pts1.add(detDesc.getLocation(i).copy());
            ds1.add(detDesc.getDescription(i).copy());
        }

        // extract features from img2
        List<Point2D_F64> pts2 = new ArrayList<>();
        List<TupleDesc_F64> ds2 = new ArrayList<>();
        detDesc.detect(gray2);
        for (int i = 0; i < detDesc.getNumberOfFeatures(); i++) {
            pts2.add(detDesc.getLocation(i).copy());
            ds2.add(detDesc.getDescription(i).copy());
        }

        if (ds1.isEmpty() || ds2.isEmpty()) {
            return fallback.findOverlap(image1, image2);
        }

        // 5) associate
        ScoreAssociation<TupleDesc_F64> score =
                FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true);
        AssociateDescription<TupleDesc_F64> matcher =
                FactoryAssociation.greedy(new ConfigAssociateGreedy(), score);

        // convert Lists to DogArray (FastAccess)
        DogArray<TupleDesc_F64> srcDesc = UtilFeature.createArrayF64(ds1.getFirst().size());
        for (TupleDesc_F64 d : ds1) {
            srcDesc.grow().setTo(d);
        }
        DogArray<TupleDesc_F64> dstDesc = UtilFeature.createArrayF64(ds2.getFirst().size());
        for (TupleDesc_F64 d : ds2) {
            dstDesc.grow().setTo(d);
        }
        matcher.setSource(srcDesc);
        matcher.setDestination(dstDesc);
        matcher.associate();

        // collect matched pairs
        FastAccess<AssociatedIndex> matches = matcher.getMatches();
        List<AssociatedPair> pairs = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            AssociatedIndex m = matches.get(i);
            pairs.add(new AssociatedPair(pts1.get(m.src), pts2.get(m.dst)));
        }
        if (pairs.size() < 10) return 0;

        // 6) RANSAC homography with increased iterations and adjusted inlier threshold
        var ransac = FactoryMultiViewRobust.homographyRansac(null, new ConfigRansac(1000, 15.0));
        Homography2D_F64 H = new Homography2D_F64();
        if (!ransac.process(pairs) || ransac.getMatchSet().size() < 10) {
            return fallback.findOverlap(image1, image2);
        }
        H.setTo(ransac.getModelParameters());

        // 7) Compute inverse homography (handle potential singular matrix)
        Homography2D_F64 H_inv = new Homography2D_F64();
        try {
            H.invert(H_inv); // Invert the homography (no boolean return)
        } catch (RuntimeException e) {
            // Fallback if matrix is singular/non-invertible
            return fallback.findOverlap(image1, image2);
        }

        // 8) Project image2's left edge into image1's coordinates
        Point2D_F64 p1 = new Point2D_F64(0, 0);
        Point2D_F64 p2 = new Point2D_F64(0, image2.getHeight() - 1);

        GeometryMath_F64.mult(H_inv.ddrm(), p1, p1);
        GeometryMath_F64.mult(H_inv.ddrm(), p2, p2);

        // Calculate average x-position in image1's space
        double avgX = (p1.x + p2.x) / 2.0;
        int ov = (int) Math.round(image1.getWidth() - avgX);

        // Validate overlap range
        int maxOverlap = Math.min(image1.getWidth(), image2.getWidth());
        ov = Math.max(0, Math.min(ov, maxOverlap));

        // Fallback if overlap is implausible
        if (ov < 10 || ov > maxOverlap * 0.9) {
            return fallback.findOverlap(image1, image2);
        }

        return ov;
    }
}
