package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.io.IOException;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.util.FetchMode.ALL;

public class GraphhopperTopEdges {
    public static void main(String[] args) throws UnsupportedEncodingException {
        // Initialize GraphHopper and load the config from file
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile("bayern-latest.osm.pbf");
        hopper.setGraphHopperLocation("test/heading-graph-cache");
        hopper.setEncodedValuesString("car_access, road_access, car_average_speed,custom_curvature, curvature, custom_curvature_score");
        hopper.setProfiles(new Profile("car").
                setCustomModel(new CustomModel().
                        addToSpeed(If("true", LIMIT, "car_average_speed")).
                        addToPriority(If("!car_access", MULTIPLY, "0")).
                        addToPriority(If("road_access == DESTINATION", MULTIPLY, "0.1"))));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        hopper.importOrLoad();

        System.out.println("Map loaded successfully. Searching for top 10 edges by curvature score...");

        // Access the graph from GraphHopper
        Graph graph = hopper.getBaseGraph();

        // Get encoded value lookup to access the custom_curvature_score
        EncodingManager encodingManager = hopper.getEncodingManager();
        IntEncodedValue curvatureScoreEnc = encodingManager.getIntEncodedValue("custom_curvature_score");

        // Create a priority queue to track the top 10 edges by curvature score
        // The queue will keep the smallest elements (lowest score) at the top
        PriorityQueue<EdgeWithCurvatureScore> topEdges = new PriorityQueue<>();

        // Create an edge explorer to iterate through all edges
        EdgeExplorer explorer = graph.createEdgeExplorer();

        // Iterate through all nodes in the graph
        for (int nodeId = 0; nodeId < graph.getNodes(); nodeId++) {
            EdgeIterator iter = explorer.setBaseNode(nodeId);

            // Iterate through all edges connected to this node
            while (iter.next()) {
                // Only process each edge once (by checking base node < adj node)
                if (iter.getBaseNode() < iter.getAdjNode()) {
                    // Get the curvature score for this edge
                    double curvatureScore = iter.get(curvatureScoreEnc);
                    double distance = iter.getDistance();
                    int edgeId = iter.getEdge();
                    int baseNode = iter.getBaseNode();
                    int adjNode = iter.getAdjNode();

                    // Get the geometry of the edge (all points)
                    PointList points = iter.fetchWayGeometry(ALL);

                    // Create an object to store edge information
                    EdgeWithCurvatureScore edge = new EdgeWithCurvatureScore(edgeId, baseNode, adjNode, curvatureScore, distance, points);

                    // Add the edge to our priority queue
                    topEdges.add(edge);

                    // If we have more than 10 edges, remove the one with the lowest score
                    if (topEdges.size() > 10) {
                        topEdges.poll();
                    }
                }
            }
        }

        // Convert the priority queue to a sorted list (highest curvature score first)
        List<EdgeWithCurvatureScore> sortedEdges = new ArrayList<>(topEdges);
        sortedEdges.sort(Comparator.comparingDouble(EdgeWithCurvatureScore::getCurvatureScore).reversed());

        // Output the top 10 edges with the highest curvature score
        System.out.println("\nTop 10 edges with the highest curvature score:");
        System.out.println("=========================================");
        System.out.println("Generated at: 2025-05-06 11:53:34 UTC by Veridus");
        System.out.println("=========================================");

        for (int i = 0; i < sortedEdges.size(); i++) {
            EdgeWithCurvatureScore edge = sortedEdges.get(i);

            System.out.println("Rank #" + (i + 1) + ":");
            System.out.println("Edge ID: " + edge.getEdgeId());
            System.out.println("Nodes: " + edge.getBaseNode() + " → " + edge.getAdjNode());
            System.out.println("Curvature Score: " + String.format("%.4f", edge.getCurvatureScore()));
            System.out.println("Distance: " + String.format("%.2f", edge.getDistance()) + " meters");

            // Create a URL to visualize the edge on the map
            String pointParam = createPointParam(edge.getPoints());
            String encodedPointParam = URLEncoder.encode(pointParam, StandardCharsets.UTF_8);
            String mapsLink = "http://localhost:8989/maps/?point=" + encodedPointParam;

            System.out.println("Map Link: " + mapsLink);
            System.out.println();
        }

        // Start the GraphHopper web server
        System.out.println("Starting GraphHopper web server...");
        System.out.println("Server will remain running until manually terminated.");
        System.out.println("Access the map interface at http://localhost:8989/");

        try {
            // Create and start the server using the configured GraphHopper instance
            new GraphHopperApplication().run(args);
        } catch (Exception e) {
            System.err.println("Error starting web server: " + e.getMessage());
            e.printStackTrace();
            // Clean up resources
            hopper.close();
        }
    }

    // Helper method to create the point parameter for the URL
    private static String createPointParam(PointList points) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                sb.append("&point=");
            }
            sb.append(points.getLat(i)).append(",").append(points.getLon(i));
        }

        return sb.toString();
    }

    // Class to represent an edge and its curvature score
    private static class EdgeWithCurvatureScore implements Comparable<EdgeWithCurvatureScore> {
        private final int edgeId;
        private final int baseNode;
        private final int adjNode;
        private final double curvatureScore;
        private final double distance;
        private final PointList points;

        public EdgeWithCurvatureScore(int edgeId, int baseNode, int adjNode, double curvatureScore, double distance, PointList points) {
            this.edgeId = edgeId;
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.curvatureScore = curvatureScore;
            this.distance = distance;
            this.points = points;
        }

        public int getEdgeId() {
            return edgeId;
        }

        public int getBaseNode() {
            return baseNode;
        }

        public int getAdjNode() {
            return adjNode;
        }

        public double getCurvatureScore() {
            return curvatureScore;
        }

        public double getDistance() {
            return distance;
        }

        public PointList getPoints() {
            return points;
        }

        @Override
        public int compareTo(EdgeWithCurvatureScore other) {
            // Natural ordering is by curvature score (lowest first)
            return Double.compare(this.curvatureScore, other.curvatureScore);
        }
    }
}
