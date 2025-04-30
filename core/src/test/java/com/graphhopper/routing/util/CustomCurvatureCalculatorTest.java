package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomCurvatureCalculatorTest {

    private EncodingManager encodingManager;
    private DecimalEncodedValue customCurvatureEnc;
    private CustomCurvatureCalculator calculator;
    // Use a constant edgeId for simplicity as ArrayEdgeIntAccess doesn't manage multiple edges
    private static final int TEST_EDGE_ID = 0;
    // Define a tolerance for floating-point comparisons
    private static final double DELTA = 1e-6;

    @BeforeEach
    void setUp() {
        // Initialize EncodedValue and Calculator before each test
        customCurvatureEnc = CustomCurvature.create(); // Assuming CustomCurvature uses 8 bits now
        encodingManager = EncodingManager.start().add(customCurvatureEnc).build();
        calculator = new CustomCurvatureCalculator(customCurvatureEnc);
    }

    /**
     * Creates a fresh ArrayEdgeIntAccess for each calculation to avoid state interference.
     * @return A new ArrayEdgeIntAccess instance.
     */
    private ArrayEdgeIntAccess createIntAccess() {
        // Size needs to accommodate the encoded value (e.g., 1 byte for 8 bits)
        return ArrayEdgeIntAccess.createFromBytes(encodingManager.getBytesForFlags());
    }

    @Test
    void testStraightWayHasMaxCurvature() {
        ArrayEdgeIntAccess intAccess = createIntAccess();
        ReaderWay straightWay = getStraightWay();

        calculator.handleWayTags(TEST_EDGE_ID, intAccess, straightWay, IntsRef.EMPTY);
        double valueStraight = customCurvatureEnc.getDecimal(false, TEST_EDGE_ID, intAccess);

        // A perfectly straight way should have the maximum curvature value (1.0)
        assertEquals(1.0, valueStraight, DELTA, "Straight way should have curvature value of 1.0");
        System.out.printf("Straight way curvature: %.4f%n", valueStraight); // Optional: print value for inspection
    }

    @Test
    void testCurvyWayHasLowerCurvatureThanStraight() {
        ArrayEdgeIntAccess intAccessStraight = createIntAccess();
        calculator.handleWayTags(TEST_EDGE_ID, intAccessStraight, getStraightWay(), IntsRef.EMPTY);
        double valueStraight = customCurvatureEnc.getDecimal(false, TEST_EDGE_ID, intAccessStraight);

        ArrayEdgeIntAccess intAccessCurvy = createIntAccess();
        calculator.handleWayTags(TEST_EDGE_ID, intAccessCurvy, getCurvyWay(), IntsRef.EMPTY);
        double valueCurvy = customCurvatureEnc.getDecimal(false, TEST_EDGE_ID, intAccessCurvy);

        assertEquals(1.0, valueStraight, DELTA, "Straight way value should be 1.0");
        assertTrue(valueCurvy < 1.0, "Curvy way value should be less than 1.0");
        assertTrue(valueCurvy >= 0.0, "Curvy way value should be non-negative");
        // This assertion remains valid and important
        assertTrue(valueCurvy < valueStraight, "Curvature value of the curvy way (" + valueCurvy + ") should be less than the straight way (" + valueStraight + ")");
        System.out.printf("Curvy way curvature: %.4f%n", valueCurvy); // Optional: print value for inspection
    }

    @Test
    void testWayWithLessThan3PointsIsStraight() {
        ArrayEdgeIntAccess intAccess = createIntAccess();
        ReaderWay way = new ReaderWay(3); // Way ID 3
        way.setTag("highway", "track");
        PointList pointList = new PointList();
        pointList.add(10.0, 10.0);
        pointList.add(10.1, 10.1); // Only 2 points
        way.setTag("point_list", pointList);

        calculator.handleWayTags(TEST_EDGE_ID, intAccess, way, IntsRef.EMPTY);
        double curvature = customCurvatureEnc.getDecimal(false, TEST_EDGE_ID, intAccess);

        assertEquals(1.0, curvature, DELTA, "Way with less than 3 points should be treated as straight (curvature 1.0)");
    }

    @Test
    void testWayWithDuplicatePointsHandled() {
        ArrayEdgeIntAccess intAccess = createIntAccess();
        ReaderWay way = new ReaderWay(4); // Way ID 4
        way.setTag("highway", "path");
        PointList pointList = new PointList();
        pointList.add(20.0, 20.0);
        pointList.add(20.0, 20.0); // Duplicate point
        pointList.add(20.1, 20.1);
        pointList.add(20.2, 20.2);
        way.setTag("point_list", pointList);

        calculator.handleWayTags(TEST_EDGE_ID, intAccess, way, IntsRef.EMPTY);
        double curvature = customCurvatureEnc.getDecimal(false, TEST_EDGE_ID, intAccess);

        // Expect it to be treated as straight locally where points are duplicated,
        // overall curvature might still be < 1.0 depending on other points.
        // Here, the remaining points form a straight line.
        assertEquals(1.0, curvature, DELTA, "Way with duplicate points causing zero distance should still calculate (expecting 1.0 here as remaining points are straight)");

        // Test with near-duplicate points causing very small distance
        intAccess = createIntAccess(); // Reset access
        pointList = new PointList();
        pointList.add(30.0, 30.0);
        pointList.add(30.0000001, 30.0000001); // Near duplicate
        pointList.add(30.1, 30.1);
        pointList.add(30.2, 30.2);
        way.setTag("point_list", pointList);

        calculator.handleWayTags(TEST_EDGE_ID, intAccess, way, IntsRef.EMPTY);
        curvature = customCurvatureEnc.getDecimal(false, TEST_EDGE_ID, intAccess);
        // If MIN_DIST_THRESHOLD works, it should skip the angle at the near-duplicate
        // and calculate based on the other points, resulting in straight here.
        assertEquals(1.0, curvature, DELTA, "Way with near-duplicate points should be handled gracefully (expecting 1.0 here)");
    }


    // --- Helper Methods ---

    private ReaderWay getStraightWay() {
        ReaderWay way = new ReaderWay(1); // Way ID 1
        PointList pointList = new PointList();
        // Ensure points are far enough apart to avoid MIN_DIST_THRESHOLD issues if tested alone
        pointList.add(50.897, 13.13);
        pointList.add(50.898, 13.13);
        pointList.add(50.899, 13.13);
        way.setTag("point_list", pointList);
        // Add dummy distance tag (not strictly needed by calculator but good practice)
        return way;
    }

    private ReaderWay getCurvyWay() {
        ReaderWay way = new ReaderWay(2); // Way ID 2 (distinct from straight way)
        PointList pointList = new PointList();
        pointList.add(50.4019683, 11.2471303); // 312429555
        pointList.add(50.4019597, 11.2467627); // 312429556
        pointList.add(50.3980572, 11.2471917); // 775752562
        pointList.add(50.3979554, 11.2465898); // 775752557
        pointList.add(50.3961790, 11.2451932); // 775752558
        pointList.add(50.3956518, 11.2435258); // 775752563
        pointList.add(50.3954909, 11.2429300); // 775752554
        pointList.add(50.3941957, 11.2407125); // 775752559
        pointList.add(50.3934662, 11.2404758); // 775752564
        pointList.add(50.3929831, 11.2424293); // 775752560
        pointList.add(50.3926294, 11.2426345); // 775752555
        pointList.add(50.3899022, 11.2436218); // 775752556
        pointList.add(50.3897083, 11.2450558); // 775752561
        pointList.add(50.3885576, 11.2451365); // 768508408
        way.setTag("point_list", pointList);
        // Add dummy distance tag
        return way;
    }
}
