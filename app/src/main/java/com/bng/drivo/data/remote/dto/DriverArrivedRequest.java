package com.bng.drivo.data.remote.dto;

public class DriverArrivedRequest {
    public double lat;
    public double lng;

    public DriverArrivedRequest(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }
}
