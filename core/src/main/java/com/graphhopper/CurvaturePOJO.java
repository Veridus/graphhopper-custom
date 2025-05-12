package com.graphhopper;

import com.fasterxml.jackson.databind.ObjectMapper; // version 2.11.1
import com.fasterxml.jackson.annotation.JsonProperty; // version 2.11.1
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/* ObjectMapper om = new ObjectMapper();
Root[] root = om.readValue(myJsonString, Root[].class); */
public class CurvaturePOJO implements Comparable<CurvaturePOJO> {
    @JsonProperty("edge_id")
    public int getEdge_id() {
        return this.edge_id;
    }

    public void setEdge_id(int edge_id) {
        this.edge_id = edge_id;
    }

    int edge_id;

    @JsonProperty("lat")
    public double getLat() {
        return this.lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    double lat;

    @JsonProperty("lon")
    public double getLon() {
        return this.lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    double lon;

    @JsonProperty("name")
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    String name;

    @JsonProperty("curvature")
    public double getCurvature() {
        return this.curvature;
    }

    public void setCurvature(double curvature) {
        this.curvature = curvature;
    }

    double curvature;

    @JsonProperty("curvature_score")
    public int getCurvature_score() {
        return this.curvature_score;
    }

    public void setCurvature_score(int curvature_score) {
        this.curvature_score = curvature_score;
    }

    int curvature_score;

    @JsonProperty("min_curvature_angle")
    public double getMin_curvature_angle() {
        return this.min_curvature_angle;
    }

    public void setMin_curvature_angle(double min_curvature_angle) {
        this.min_curvature_angle = min_curvature_angle;
    }

    double min_curvature_angle;

    @Override
    public int compareTo(CurvaturePOJO other) {
        // Natural ordering is by curvature score (lowest first)
        return Integer.compare(this.curvature_score, other.curvature_score);
    }
}

