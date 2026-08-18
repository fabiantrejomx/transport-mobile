package com.bng.drivo.data.remote.dto;

public class RatingRequest {
    public int stars;
    public String comment;

    public RatingRequest(int stars, String comment) {
        this.stars = stars;
        this.comment = comment;
    }
}
