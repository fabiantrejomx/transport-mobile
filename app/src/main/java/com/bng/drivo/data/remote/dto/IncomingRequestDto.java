package com.bng.drivo.data.remote.dto;

import java.util.List;

public class IncomingRequestDto {
    public String ride_id;
    public PassengerSummaryDto passenger;
    public double offer;
    public PlaceDto pickup;
    public PlaceDto dropoff;
    public Integer pickup_distance_m;
    public Integer pickup_eta_min;
    public Integer trip_distance_m;
    /** Ya recortados al techo del viaje por el servidor; puede venir vacío. Nunca fijarlos en la UI. */
    public List<Double> counter_increments;
    public String expires_at;
}
