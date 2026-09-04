package com.bng.drivo.data.model;

import androidx.annotation.Nullable;

/** Cotización de POST /quotes: el slider se mueve entre floor y ceiling, nunca calculados en el cliente. */
public class Quote {

    private final String id;
    private final double suggestedFare;
    private final double floor;
    private final double ceiling;
    @Nullable
    private final String polyline;

    public Quote(String id, double suggestedFare, double floor, double ceiling,
                 @Nullable String polyline) {
        this.id = id;
        this.suggestedFare = suggestedFare;
        this.floor = floor;
        this.ceiling = ceiling;
        this.polyline = polyline;
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

    /**
     * Trazo de la ruta por calles (origen → paradas → destino) codificado, tal como lo devolvió
     * el servidor. Null cuando Google omitió el campo: el mapa cae entonces a la guía recta.
     */
    @Nullable
    public String getPolyline() {
        return polyline;
    }
}
