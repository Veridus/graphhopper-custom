package com.graphhopper.routing.ev;

/**
 * Defines the EncodedValue for storing custom curvature.
 * Curvature is stored as a decimal value between 0.0 (sharpest curve) and 1.0 (straight line).
 * This value is calculated by {@link com.graphhopper.routing.util.CustomCurvatureCalculator}.
 *
 * This version uses more bits for increased precision compared to a 4-bit implementation.
 */
public class CustomCurvature {
    /**
     * The key used to identify this EncodedValue in the graph storage.
     */
    public static final String KEY = "custom_curvature";

    /**
     * Creates a DecimalEncodedValue instance configured for storing curvature with higher precision.
     *
     * The configuration maps the calculated curvature range [0.0, 1.0] to the
     * discrete integer values storable within the specified number of bits.
     *
     * @return A configured DecimalEncodedValue for curvature.
     */
    public static DecimalEncodedValue create() {
        // Number of bits used to store the curvature value.
        // 8 bits allow for 2^8 = 256 distinct values, providing higher precision than 4 bits.
        final int bits = 8;

        // The minimum decimal value representable (corresponds to the sharpest curve).
        final double minValue = 0.0;

        // The maximum decimal value representable (corresponds to a straight line).
        final double maxValue = 1.0;

        // The factor determines the precision step (size of each quantization level).
        // With more bits, the factor becomes smaller, representing finer steps.
        // factor = (maxValue - minValue) / (number_of_steps)
        // number_of_steps = 2^bits - 1
        final double factor = (maxValue - minValue) / ((1 << bits) - 1); // (1.0 - 0.0) / (256 - 1) = 1.0 / 255

        // Store the value only for the forward direction of the edge.
        final boolean storeTwoDirections = false;

        // Create the DecimalEncodedValue using the implementation class.
        // Assumes: DecimalEncodedValueImpl(name, bits, minValue, factor, storeTwoDirections, negateReverse, allowZero)
        return new DecimalEncodedValueImpl(KEY, bits, minValue, factor, storeTwoDirections, false, false);
    }
}
