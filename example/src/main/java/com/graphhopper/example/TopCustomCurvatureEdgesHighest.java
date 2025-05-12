package com.graphhopper.example;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.CurvatureScore;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lists the top N edges with the highest custom curvature score in a GraphHopper graph,
 * printing edge id, street name, lat/lon coordinates of the edge's base and adj nodes,
 * a maps link for each edge, and a histogram of all scores.
 * <p>
 * Make sure to use the same encoded value settings as used during import.
 */
public class TopCustomCurvatureEdgesHighest {
    public static void main(String[] args) {
        System.out.println(System.getProperty("user.dir"));
        int n = 50;
        String graphLocation = "graph-cache";
        // Register your IntEncodedValue as during import
        IntEncodedValue curvatureEnc = CurvatureScore.create();
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        EncodingManager em = EncodingManager.start().add(accessEnc).add(curvatureEnc).build();

        BaseGraph graph = new BaseGraph.Builder(em).setDir(new RAMDirectory(graphLocation, true)).build();
        if (!graph.loadExisting()) {
            System.err.println("Could not load existing graph at " + graphLocation);
            return;
        }

        // Min-heap for highest scores (we'll use a min-heap but invert the comparison)
        PriorityQueue<EdgeScore> topEdges = new PriorityQueue<>(Comparator.comparingInt(e -> e.score));
        Map<Integer, Integer> histogram = new TreeMap<>();
        int foundMax = Integer.MIN_VALUE;
        int foundMin = Integer.MAX_VALUE;

        EdgeIterator iter = graph.getAllEdges();
        while (iter.next()) {
            EdgeIteratorState edge = iter;
            int score = edge.get(curvatureEnc);
            int edgeId = edge.getEdge();

            // Build histogram
            histogram.put(score, histogram.getOrDefault(score, 0) + 1);

            // Track min/max
            if (score > foundMax) foundMax = score;
            if (score < foundMin) foundMin = score;

            // Track top edges: keep n edges with the highest scores
            if (topEdges.size() < n) {
                topEdges.add(new EdgeScore(edgeId, score));
            } else if (score > topEdges.peek().score) {
                topEdges.poll();
                topEdges.add(new EdgeScore(edgeId, score));
            }
        }

        List<EdgeScore> result = new ArrayList<>(topEdges);
        result.sort(Comparator.comparingInt(a -> -a.score)); // descending order (highest first)

        NodeAccess na = graph.getNodeAccess();

        System.out.println("Top " + n + " edges by CustomCurvatureScore:");
        for (EdgeScore es : result) {
            EdgeIteratorState edge = graph.getEdgeIteratorState(es.edgeId, Integer.MIN_VALUE);
            int base = edge.getBaseNode();
            int adj = edge.getAdjNode();

            double baseLat = na.getLat(base);
            double baseLon = na.getLon(base);
            double adjLat = na.getLat(adj);
            double adjLon = na.getLon(adj);

            String streetName = edge.getName();
            if (streetName == null || streetName.isEmpty()) {
                streetName = "unknown";
            }

            // Build the maps link as requested
            String pointParam = String.format(Locale.ROOT, "%.6f,%.6f_%.6f,%.6f", baseLat, baseLon, adjLat, adjLon);
            String encodedPointParam = URLEncoder.encode(pointParam, StandardCharsets.UTF_8);
            String mapsLink = "http://localhost:8989/maps/?point=" + encodedPointParam;

            System.out.printf(
                    "Edge %d | Score: %d | Street: %s | From: (%.6f, %.6f) | To: (%.6f, %.6f) | Link: %s%n",
                    es.edgeId, es.score, streetName, baseLat, baseLon, adjLat, adjLon, mapsLink
            );

            // ENHANCED DEBUGGING: Print detailed edge information
            System.out.println("  Edge Tags/Properties:");

            // Basic edge properties
            System.out.printf("    Edge ID: %d%n", edge.getEdge());
            System.out.printf("    Base Node: %d, Adj Node: %d%n", edge.getBaseNode(), edge.getAdjNode());
            System.out.printf("    CustomCurvatureScore: %d%n", edge.get(curvatureEnc));
            System.out.printf("    Car Access: %b%n", edge.get(accessEnc));
            System.out.printf("    Distance: %.2f meters%n", edge.getDistance());

            // Avoid using getFlags() directly since it returns IntsRef
            System.out.println("    Encoded Values:");
            try {
                // Try to access backward access for this edge (if available)
                EdgeIteratorState oppositeEdge = graph.getEdgeIteratorState(es.edgeId, base);
                if (oppositeEdge != null) {
                    System.out.printf("      Opposite Direction Access: %b%n", oppositeEdge.get(accessEnc));
                }
            } catch (Exception e) {
                // Ignore if not available
            }

            // Try to get all encoded values registered in EncodingManager
            try {
                String emString = em.toString();
                System.out.println("    EncodingManager: " + emString);
            } catch (Exception e) {
                System.out.println("    Error accessing EncodingManager: " + e.getMessage());
            }

            // Get and print detailed way geometry
            try {
                PointList points = edge.fetchWayGeometry(FetchMode.ALL);
                System.out.printf("    Way Geometry (%d points):%n", points.size());

                for (int i = 0; i < points.size(); i++) {
                    System.out.printf("      Point %d: (%.6f, %.6f)%n",
                            i, points.getLat(i), points.getLon(i));
                }

                // Calculate actual curvature based on geometry points to verify
                if (points.size() >= 3) {
                    double calculatedCurvature = calculateCurvature(points);
                    System.out.printf("    Calculated Curvature (from points): %.6f%n", calculatedCurvature);
                }
            } catch (Exception e) {
                System.out.println("    Error fetching way geometry: " + e.getMessage());
            }

            // Check connected edges and their scores
            try {
                System.out.println("    Connected Edges:");
                EdgeExplorer explorer = graph.createEdgeExplorer();
                EdgeIterator connectedEdges = explorer.setBaseNode(base);
                int count = 0;
                while (connectedEdges.next() && count < 5) {
                    if (connectedEdges.getEdge() != edge.getEdge()) {
                        System.out.printf("      Edge %d: Score %d, Name: %s%n",
                                connectedEdges.getEdge(),
                                connectedEdges.get(curvatureEnc),
                                connectedEdges.getName());
                        count++;
                    }
                }

                connectedEdges = explorer.setBaseNode(adj);
                count = 0;
                while (connectedEdges.next() && count < 5) {
                    if (connectedEdges.getEdge() != edge.getEdge()) {
                        System.out.printf("      Edge %d: Score %d, Name: %s%n",
                                connectedEdges.getEdge(),
                                connectedEdges.get(curvatureEnc),
                                connectedEdges.getName());
                        count++;
                    }
                }
            } catch (Exception e) {
                System.out.println("    Error exploring connected edges: " + e.getMessage());
            }

            System.out.println();  // Empty line between edges for better readability
        }

        // Print histogram and min/max values for score
        System.out.println("\nHistogram of all curvature scores:");
        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
            System.out.printf("Score %d: %d edges%n", entry.getKey(), entry.getValue());
        }
        System.out.printf("Curvature min: %d, max: %d%n", foundMin, foundMax);

        graph.close();
    }

    // Calculates a simple curvature metric from a point list
    private static double calculateCurvature(PointList points) {
        if (points.size() < 3) return 0;

        double totalAngleChange = 0;
        double totalDistance = 0;

        for (int i = 1; i < points.size() - 1; i++) {
            double lat1 = points.getLat(i-1);
            double lon1 = points.getLon(i-1);
            double lat2 = points.getLat(i);
            double lon2 = points.getLon(i);
            double lat3 = points.getLat(i+1);
            double lon3 = points.getLon(i+1);

            // Calculate vectors
            double[] v1 = {lat2 - lat1, lon2 - lon1};
            double[] v2 = {lat3 - lat2, lon3 - lon2};

            // Normalize vectors
            double v1Length = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1]);
            double v2Length = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1]);

            if (v1Length > 0 && v2Length > 0) {
                v1[0] /= v1Length;
                v1[1] /= v1Length;
                v2[0] /= v2Length;
                v2[1] /= v2Length;

                // Calculate dot product and angle between vectors
                double dotProduct = v1[0] * v2[0] + v1[1] * v2[1];
                if (dotProduct > 1) dotProduct = 1;
                if (dotProduct < -1) dotProduct = -1;

                double angleChange = Math.acos(dotProduct);
                totalAngleChange += angleChange;

                // Add segment distance
                totalDistance += distanceInMeters(lat1, lon1, lat2, lon2);
            }
        }

        // Adding the last segment distance
        totalDistance += distanceInMeters(
                points.getLat(points.size() - 2),
                points.getLon(points.size() - 2),
                points.getLat(points.size() - 1),
                points.getLon(points.size() - 1)
        );

        // Return curvature as angle change per distance
        return totalDistance > 0 ? totalAngleChange / totalDistance : 0;
    }

    // Simple haversine distance calculation
    private static double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    static class EdgeScore {
        int edgeId;
        int score;

        EdgeScore(int edgeId, int score) {
            this.edgeId = edgeId;
            this.score = score;
        }
    }
}
