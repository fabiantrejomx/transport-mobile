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
    public String expires_at;
}
