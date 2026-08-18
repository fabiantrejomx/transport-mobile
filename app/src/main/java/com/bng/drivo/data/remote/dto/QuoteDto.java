package com.bng.drivo.data.remote.dto;

public class QuoteDto {
    public String id;
    public double suggested_fare;
    public double floor;
    public double ceiling;
    public long distance_m;
    public long duration_s;
    public Double cost_per_min_applied;
    public String origin_text;
    public String dest_text;
    public String expires_at;
}
