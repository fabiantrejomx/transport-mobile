package com.bng.drivo.data.remote.dto;

import java.util.List;

public class QuoteRequest {
    public LatLngDto origin;
    public LatLngDto destination;
    public List<LatLngDto> waypoints;
    public String origin_text;
    public String dest_text;

    public QuoteRequest(LatLngDto origin, LatLngDto destination, List<LatLngDto> waypoints,
                         String originText, String destText) {
        this.origin = origin;
        this.destination = destination;
        this.waypoints = waypoints;
        this.origin_text = originText;
        this.dest_text = destText;
    }
}
