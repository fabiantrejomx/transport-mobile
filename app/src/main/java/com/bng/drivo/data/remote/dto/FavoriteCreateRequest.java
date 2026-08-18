package com.bng.drivo.data.remote.dto;

public class FavoriteCreateRequest {
    public String label;
    public String address_text;
    public double lat;
    public double lng;

    public FavoriteCreateRequest(String label, String addressText, double lat, double lng) {
        this.label = label;
        this.address_text = addressText;
        this.lat = lat;
        this.lng = lng;
    }
}
