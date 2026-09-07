package com.bng.drivo.data.remote.dto;

public class RideSummaryDto {
    public String id;
    public String status;
    public Double agreed_fare;
    public String origin_text;
    public String dest_text;
    public String requested_at;
    /**
     * Las estrellas que <b>este</b> lado ya le puso al viaje, o null si todavía no lo calificó.
     * El {@code role} de la consulta decide de qué lado se mira.
     *
     * <p>Un {@code COMPLETED} con {@code my_rating} null es una calificación pendiente, y es la
     * única forma de saberlo: la pantalla de recibo vive en memoria y se la lleva cualquier cierre
     * de la app.
     */
    public Integer my_rating;
    /**
     * Si el viaje se cerró antes de llegar al destino. Va en el historial y no solo en el detalle
     * porque un renglón {@code COMPLETED} con el destino pedido, sin más, cuenta un viaje que no
     * ocurrió así. Puede venir null de un servidor anterior: es lo mismo que false.
     */
    public Boolean ended_early;
}
