package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates a curvature score based on the sum of angle deviations per unit distance.
 * Stores the result in an IntEncodedValue. A score of 0 means straight, higher scores
 * indicate more curvature/twistiness.
 */
public class CurvatureScoreCalculator implements TagParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurvatureScoreCalculator.class);

    private final IntEncodedValue curvatureScoreEnc;
    private final int maxScore;
    private final double scalingFactor;
    private final DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;
    private static final double MIN_DIST_THRESHOLD = 1e-7; // Keep minimum distance check
    private static final double MIN_TOTAL_DISTANCE_THRESHOLD = 1.0; // Min distance in meters to calculate score for

    /**
     * Constructor.
     * @param scoreEnc The IntEncodedValue to store the score.
     * @param scalingFactor A factor to multiply the (radians/meter) metric before rounding and capping.
     *                      Needs tuning based on expected road types and desired score distribution.
     *                      A higher factor stretches the scores out more. Example: 4000.
     */
    public CurvatureScoreCalculator(IntEncodedValue scoreEnc, double scalingFactor) {
        this.curvatureScoreEnc = scoreEnc;
        // Calculate max storable score based on bits used by the IntEncodedValue
        this.maxScore = scoreEnc.getMaxStorableInt();
        this.scalingFactor = scalingFactor;
        LOGGER.info("Initialized CurvatureScoreCalculator with maxScore={}, scalingFactor={}", maxScore, scalingFactor);
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        PointList pointList = way.getTag("point_list", null);
        // Get total distance for normalization. If not present or too small, score as straight.
        double totalDistance = way.getTag("edge_distance", 0.0);

        if (pointList == null || pointList.size() < 3 || totalDistance < MIN_TOTAL_DISTANCE_THRESHOLD) {
            curvatureScoreEnc.setInt(false, edgeId, edgeIntAccess, 0); // Score 0 for straight/short/invalid
            return;
        }

        double totalDeviation = 0.0; // Sum of (PI - angle) in radians

        for (int i = 0; i < pointList.size() - 2; i++) {
            double latA = pointList.getLat(i);
            double lonA = pointList.getLon(i);
            double latB = pointList.getLat(i + 1);
            double lonB = pointList.getLon(i + 1);
            double latC = pointList.getLat(i + 2);
            double lonC = pointList.getLon(i + 2);

            double distAB = distCalc.calcDist(latA, lonA, latB, lonB);
            double distBC = distCalc.calcDist(latB, lonB, latC, lonC);

            if (distAB < MIN_DIST_THRESHOLD || distBC < MIN_DIST_THRESHOLD) {
                // Treat as locally straight, add 0 deviation for this vertex
                continue;
            }

            double distAC = distCalc.calcDist(latA, lonA, latC, lonC);
            double cosAngleB = (distAB * distAB + distBC * distBC - distAC * distAC) / (2 * distAB * distBC);
            cosAngleB = Math.max(-1.0, Math.min(1.0, cosAngleB));
            double angleB = Math.acos(cosAngleB); // Angle in radians [0, PI]

            // Deviation from straight (PI radians or 180 degrees)
            double deviation = Math.PI - angleB;
            totalDeviation += deviation;
        }

        // Calculate metric: total deviation per meter
        double metric = totalDeviation / totalDistance; // radians per meter

        // Scale, round, and cap the score
        int score = (int) Math.min(maxScore, Math.round(metric * scalingFactor));

        // Ensure score is non-negative (should be, but safety check)
        score = Math.max(0, score);

        curvatureScoreEnc.setInt(false, edgeId, edgeIntAccess, score);

        // Optional: Log calculated metric and score for tuning
        // if (score > 0) {
        //     LOGGER.debug("Way ID: {}, Distance: {:.1f}m, TotalDev: {:.2f}rad, Metric: {:.4f}rad/m, Score: {}",
        //                  way.getId(), totalDistance, totalDeviation, metric, score);
        // }
    }
}
