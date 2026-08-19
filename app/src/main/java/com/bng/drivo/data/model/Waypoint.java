package com.bng.drivo.data.model;

/** Parada intermedia opcional para POST /quotes — el contrato ya la soporta (waypoints). */
public class Waypoint {

    private final double lat;
    private final double lng;
    private final String text;

    public Waypoint(double lat, double lng, String text) {
        this.lat = lat;
        this.lng = lng;
        this.text = text;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public String getText() {
        return text;
    }
}
