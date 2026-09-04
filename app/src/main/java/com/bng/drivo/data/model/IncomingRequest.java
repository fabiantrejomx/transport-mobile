package com.bng.drivo.data.model;

import java.util.Collections;
import java.util.List;

/** GET /driver/rides/{id} — una solicitud entrante para el conductor. */
public class IncomingRequest {

    private final String rideId;
    private final String passengerName;
    private final Double passengerRating;
    private final Integer passengerTrips;
    private final double offer;
    private final String pickupText;
    private final String dropoffText;
    private final Double pickupLat;
    private final Double pickupLng;
    private final Double dropoffLat;
    private final Double dropoffLng;
    /** Paradas intermedias, en orden; vacía si el viaje no tiene o si el servidor no las manda. */
    private final List<Waypoint> stops;
    private final Integer pickupDistanceM;
    private final Integer pickupEtaMin;
    private final Integer tripDistanceM;
    private final List<Double> counterIncrements;
    /** Trazo del viaje por calles, codificado. Null si el servidor no lo mandó: guía recta. */
    private final String polyline;
    private final String expiresAt;

    public IncomingRequest(String rideId, String passengerName, Double passengerRating, Integer passengerTrips,
                            double offer, String pickupText, String dropoffText, Double pickupLat, Double pickupLng,
                            Double dropoffLat, Double dropoffLng, List<Waypoint> stops,
                            Integer pickupDistanceM, Integer pickupEtaMin,
                            Integer tripDistanceM, List<Double> counterIncrements, String polyline,
                            String expiresAt) {
        this.rideId = rideId;
        this.passengerName = passengerName;
        this.passengerRating = passengerRating;
        this.passengerTrips = passengerTrips;
        this.offer = offer;
        this.pickupText = pickupText;
        this.dropoffText = dropoffText;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
        this.stops = stops != null ? stops : Collections.emptyList();
        this.pickupDistanceM = pickupDistanceM;
        this.pickupEtaMin = pickupEtaMin;
        this.tripDistanceM = tripDistanceM;
        this.counterIncrements = counterIncrements;
        this.polyline = polyline;
        this.expiresAt = expiresAt;
    }

    public String getRideId() {
        return rideId;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public Double getPassengerRating() {
        return passengerRating;
    }

    public Integer getPassengerTrips() {
        return passengerTrips;
    }

    public double getOffer() {
        return offer;
    }

    public String getPickupText() {
        return pickupText;
    }

    public String getDropoffText() {
        return dropoffText;
    }

    public Double getPickupLat() {
        return pickupLat;
    }

    public Double getPickupLng() {
        return pickupLng;
    }

    public Double getDropoffLat() {
        return dropoffLat;
    }

    public Double getDropoffLng() {
        return dropoffLng;
    }

    /** Nunca null: lista vacía cuando el viaje no tiene paradas. */
    public List<Waypoint> getStops() {
        return stops;
    }

    public Integer getPickupDistanceM() {
        return pickupDistanceM;
    }

    public Integer getPickupEtaMin() {
        return pickupEtaMin;
    }

    public Integer getTripDistanceM() {
        return tripDistanceM;
    }

    /** Ya recortados al techo del viaje por el servidor; puede venir vacío. Nunca fijarlos en la UI. */
    public List<Double> getCounterIncrements() {
        return counterIncrements;
    }

    /** Ver {@link #polyline}: null cuando no hay trazo y toca la guía recta. */
    public String getPolyline() {
        return polyline;
    }

    public String getExpiresAt() {
        return expiresAt;
    }
}
