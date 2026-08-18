package com.bng.drivo.data.remote.dto;

public class CreateRideRequest {
    public String quote_id;
    public double offer;

    public CreateRideRequest(String quoteId, double offer) {
        this.quote_id = quoteId;
        this.offer = offer;
    }
}
