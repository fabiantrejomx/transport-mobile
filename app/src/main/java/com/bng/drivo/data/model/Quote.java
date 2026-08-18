package com.bng.drivo.data.model;

/** Cotización de POST /quotes: el slider se mueve entre floor y ceiling, nunca calculados en el cliente. */
public class Quote {

    private final String id;
    private final double suggestedFare;
    private final double floor;
    private final double ceiling;

    public Quote(String id, double suggestedFare, double floor, double ceiling) {
        this.id = id;
        this.suggestedFare = suggestedFare;
        this.floor = floor;
        this.ceiling = ceiling;
    }

    public String getId() {
        return id;
    }

    public double getSuggestedFare() {
        return suggestedFare;
    }

    public double getFloor() {
        return floor;
    }

    public double getCeiling() {
        return ceiling;
    }
}
