package com.bng.drivo.data.remote.dto;

import java.util.List;

public class RideDto {
    public String id;
    public String status;
    public DriverSummaryDto driver;
    /** Lo que el pasajero ofreció al pedir el viaje. No cambia; el acordado es {@link #agreed_fare}. */
    public Double passenger_offer;
    public Double agreed_fare;
    public PlaceDto origin;
    public PlaceDto destination;
    /**
     * Paradas intermedias, en orden. Vienen sin {@code text}: la parada se elige sobre el mapa y
     * la dirección la resuelve este teléfono, así que el servidor nunca llegó a tenerla.
     *
     * <p>Existen para poder retomar un viaje: sin ellas, un viaje releído —al reanudar la app, al
     * abrirlo desde una notificación— dibujaría origen→destino saltándose la parada del propio
     * pasajero. Puede venir null si el servidor es anterior al contrato que las trae.
     */
    public List<LatLngDto> waypoints;
    /**
     * La misma polilínea de la cotización, copiada por el servidor: la cotización vence a los 5
     * minutos y el viaje dura mucho más. Puede venir null; ver {@link QuoteDto#polyline}.
     */
    public String polyline;
    public String requested_at;
    /**
     * Cuándo deja de buscarse conductor (ISO-8601). Es el único reloj de la subasta: las ofertas
     * vencen con ella. Sirve para que una app que vuelve a media búsqueda pinte el tiempo que de
     * verdad queda en vez de reiniciar la cuenta.
     */
    public String search_expires_at;
    public String driver_arrived_at;
    public String completed_at;
    /** Solo viene poblado en la respuesta de POST /driver/rides/{id}/complete. */
    public Double commission;
}
