package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;

/**
 * Calculates a custom curvature metric based on the angles between consecutive segments of a way.
 * The metric is stored in a DecimalEncodedValue. A value of 1.0 represents a straight segment,
 * while smaller values represent sharper curves.
 */
public class MinCurvatureAngleCalculator implements TagParser {

    private final DecimalEncodedValue customCurvatureEnc;
    // Use Earth distance calculation
    private final DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;
    // Minimum distance threshold to consider points distinct for angle calculation (in meters)
    private static final double MIN_DIST_THRESHOLD = 1e-7;

    public MinCurvatureAngleCalculator(DecimalEncodedValue customCurvatureEnc) {
        this.customCurvatureEnc = customCurvatureEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        PointList pointList = way.getTag("point_list", null);
        // String wayType = way.getTag("highway", null);

        // We need at least 3 points to calculate an angle
        if (pointList != null && pointList.size() >= 3) {
            double minAngle = Math.PI; // Initialize with the angle of a straight line (180 degrees)

            // Iterate through points [0,1,2], [1,2,3], ..., [n-3, n-2, n-1]
            // size => 6 | size - 2 => 4 => indices [0, 1, 2, 3]
            for (int i = 0; i < pointList.size() - 2; i++) {
                // Get triple of points (A, B, C)
                double latA = pointList.getLat(i);
                double lonA = pointList.getLon(i);

                double latB = pointList.getLat(i + 1);
                double lonB = pointList.getLon(i + 1);

                double latC = pointList.getLat(i + 2);
                double lonC = pointList.getLon(i + 2);

                // Calculate distances ab, bc, and ac
                double distAB = distCalc.calcDist(latA, lonA, latB, lonB);
                double distBC = distCalc.calcDist(latB, lonB, latC, lonC);

                // Check for very short segments (duplicate points or noise)
                if (distAB < MIN_DIST_THRESHOLD || distBC < MIN_DIST_THRESHOLD) {
                    // Treat as straight locally, continue to next triplet
                    continue;
                }

                double distAC = distCalc.calcDist(latA, lonA, latC, lonC);

                // Calculate angle at point B using the Law of Cosines:
                // AC^2 = AB^2 + BC^2 - 2 * AB * BC * cos(angleB)
                // cos(angleB) = (AB^2 + BC^2 - AC^2) / (2 * AB * BC)
                double cosAngleB = (distAB * distAB + distBC * distBC - distAC * distAC) / (2 * distAB * distBC);

                // Clamp the value to [-1, 1] due to potential floating-point inaccuracies
                cosAngleB = Math.max(-1.0, Math.min(1.0, cosAngleB));

                double angleB = Math.acos(cosAngleB); // Angle in radians [0, PI]

                // Keep the smallest angle found so far (smallest angle -> sharpest curve)
                minAngle = Math.min(minAngle, angleB);
            }

            // Normalize the angle: straight line (PI) -> 1.0, sharpest turn (0) -> 0.0
            // If minAngle is still PI (or very close), it means all segments were collinear or too short.
            double curvature = (minAngle == Math.PI) ? 1.0 : minAngle / Math.PI;

            // Ensure the value is within the storable range of the EncodedValue
            double storableCurvature = Math.max(customCurvatureEnc.getMinStorableDecimal(), Math.min(customCurvatureEnc.getMaxStorableDecimal(), curvature));

            customCurvatureEnc.setDecimal(false, edgeId, edgeIntAccess, storableCurvature);

        } else {
            // If there are less than 3 points, we cannot calculate curvature, assume straight.
            customCurvatureEnc.setDecimal(false, edgeId, edgeIntAccess, 1.0);
        }
    }
}
