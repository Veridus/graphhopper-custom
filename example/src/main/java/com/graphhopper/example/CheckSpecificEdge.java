package com.graphhopper.example;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Checks a specific edge in the graph to verify its CustomCurvatureScore and distance
 */
public class CheckSpecificEdge {
    public static void main(String[] args) {
        // Edge ID to check
        int edgeId = 767813;

        String graphLocation = "graph-cache";
        IntEncodedValue curvatureEnc = CurvatureScore.create();
        DecimalEncodedValue curvatureEncDefault = Curvature.create();
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        EncodingManager em = EncodingManager.start().add(accessEnc).add(curvatureEnc).add(curvatureEncDefault).build();

        BaseGraph graph = new BaseGraph.Builder(em).setDir(new RAMDirectory(graphLocation, true)).build();
        if (!graph.loadExisting()) {
            System.err.println("Could not load existing graph at " + graphLocation);
            return;
        }

        System.out.println("CHECKING EDGE " + edgeId);
        System.out.println("============================");

        try {
            // Get the edge by ID
            EdgeIteratorState edge = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);

            // Basic edge information
            int base = edge.getBaseNode();
            int adj = edge.getAdjNode();
            String name = edge.getName();
            name = (name != null && !name.isEmpty()) ? name : "unknown";

            NodeAccess na = graph.getNodeAccess();
            double baseLat = na.getLat(base);
            double baseLon = na.getLon(base);
            double adjLat = na.getLat(adj);
            double adjLon = na.getLon(adj);

            // Get the key values we want to check
            int curvatureScore = edge.get(curvatureEnc);
            double curvatureScoreDef = edge.get(curvatureEncDefault);

            double distance = edge.getDistance();

            System.out.println("Edge ID: " + edgeId);
            System.out.println("Street Name: " + name);
            System.out.println("Base Node: " + base + " (" + baseLat + ", " + baseLon + ")");
            System.out.println("Adj Node: " + adj + " (" + adjLat + ", " + adjLon + ")");
            System.out.println("CustomCurvatureScore: " + curvatureScore);
            System.out.println("Default Curvature: " + curvatureScoreDef);
            System.out.println("Distance: " + distance + " meters");

            System.out.println("\nGEOMETRY ANALYSIS");
            System.out.println("----------------");

            // Compare with expected values
            System.out.println("\nCOMPARISON WITH EXPECTED VALUES");
            System.out.println("--------------------------------");
            System.out.println("Expected CustomCurvatureScore: 83, Actual: " + curvatureScore);
            System.out.println("Expected Distance: 1072.771 meters, Actual: " + distance);
            System.out.println("Default Curvature from GraphHopper: " + curvatureScoreDef);

            // Analysis of differences
            System.out.println("\nANALYSIS");
            System.out.println("--------");

            if (Math.abs(curvatureScore - 83) > 0) {
                System.out.println("WARNING: CustomCurvatureScore differs from expected value (83)");
            } else {
                System.out.println("CustomCurvatureScore matches expected value (83)");
            }

            if (Math.abs(distance - 1072.771) > 1.0) {
                System.out.println("WARNING: Distance differs from expected value (1072.771)");
            } else {
                System.out.println("Distance matches expected value (1072.771)");
            }


            // Check relationship between CustomCurvatureScore and default curvature
            System.out.println("\nRelationship between curvature values:");
            System.out.println("CustomCurvatureScore (int): " + curvatureScore);
            System.out.println("Default Curvature (decimal): " + curvatureScoreDef);

        } catch (Exception e) {
            System.err.println("Error checking edge " + edgeId + ": " + e.getMessage());
            e.printStackTrace();
        }

        graph.close();
    }


}
