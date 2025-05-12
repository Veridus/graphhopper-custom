package com.graphhopper.routing.ev;

import com.graphhopper.routing.util.CurvatureScoreCalculator;

/**
 * Defines an IntEncodedValue for storing a curvature score.
 * A score of 0 represents a straight way, while higher scores indicate
 * increasing levels of curvature or "twistiness", calculated based on the
 * sum of angle deviations per unit distance.
 *
 * Calculated by {@link CurvatureScoreCalculator}.
 */
public class CurvatureScore {
    public static final String KEY = "curvature_score";

    /**
     * Creates an IntEncodedValue for the curvature score.
     * @param bits The number of bits to allocate (e.g., 6 bits for scores 0-63).
     *             More bits allow for finer score graduation.
     * @return A configured IntEncodedValue.
     */
    public static IntEncodedValue create(int bits) {
        // Max score will be 2^bits - 1
        // Store only for forward direction by default.
        boolean storeTwoDirections = false;
        return new IntEncodedValueImpl(KEY, bits, storeTwoDirections);
    }

    /**
     * Creates an IntEncodedValue with a default number of bits (e.g., 6).
     */
    public static IntEncodedValue create() {
        return create(8); // Default to 6 bits (scores 0-63)
    }
}
