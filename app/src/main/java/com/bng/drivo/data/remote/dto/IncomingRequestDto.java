package com.bng.drivo.data.remote.dto;

import java.util.List;

public class IncomingRequestDto {
    public String ride_id;
    public PassengerSummaryDto passenger;
    public double offer;
    public PlaceDto pickup;
    public PlaceDto dropoff;
    /**
     * Paradas intermedias del viaje del pasajero, en orden. El pasajero las manda al cotizar
     * (waypoints de POST /quotes) y el contrato todavía no las documenta en esta respuesta: si el
     * servidor no las incluye, Gson deja el campo en null y la app simplemente no pinta ninguna
     * parada. Nunca dar por hecho que viene.
     */
    public List<PlaceDto> waypoints;
    public Integer pickup_distance_m;
    public Integer pickup_eta_min;
    public Integer trip_distance_m;
    /** Ya recortados al techo del viaje por el servidor; puede venir vacío. Nunca fijarlos en la UI. */
    public List<Double> counter_increments;
    /**
     * Trazo del viaje del pasajero por calles, codificado (contrato 1.7.0). Es el mismo de la
     * cotización, leído de la fila del viaje, así que servirlo no le cuesta al servidor otra
     * llamada a Google. Puede venir null —el servidor puede no haberlo recibido, o estar aún en
     * una versión anterior del contrato— y entonces el mapa pinta la guía recta.
     *
     * <p>No cubre el tramo de recogida, del conductor al pasajero: ese se pinta recto siempre.
     */
    public String polyline;
    public String expires_at;
}
