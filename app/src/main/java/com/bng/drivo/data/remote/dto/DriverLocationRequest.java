package com.bng.drivo.data.remote.dto;

public class DriverLocationRequest {
    public double lat;
    public double lng;
    public Double heading;
    public Double accuracy_m;

    public DriverLocationRequest(double lat, double lng, Double heading, Double accuracyM) {
        this.lat = lat;
        this.lng = lng;
        this.heading = heading;
        this.accuracy_m = accuracyM;
    }
}
