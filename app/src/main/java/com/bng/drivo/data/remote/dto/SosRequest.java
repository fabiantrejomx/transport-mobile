package com.bng.drivo.data.remote.dto;

public class SosRequest {
    public String ride_id;
    public double lat;
    public double lng;

    public SosRequest(String rideId, double lat, double lng) {
        this.ride_id = rideId;
        this.lat = lat;
        this.lng = lng;
    }
}
