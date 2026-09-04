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
    /**
     * Trazo de la ruta por calles, codificado (algoritmo de polilíneas de Google). Solo para
     * dibujar: la distancia, la duración y la tarifa las sigue poniendo el servidor. Puede venir
     * null —Google a veces omite el campo— y eso nunca invalida la cotización.
     */
    public String polyline;
    public String expires_at;
}
