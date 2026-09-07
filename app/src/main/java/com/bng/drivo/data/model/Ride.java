package com.bng.drivo.data.model;

import java.util.Collections;
import java.util.List;

/** Se amplía conforme cada sub-fase de "Fase 6" lo necesita. originText/destinationText/
 * requestedAt solo los llena GET /rides/{id} — las demás llamadas (crear, aceptar, cancelar)
 * los dejan null, ya conocen origen/destino por otro lado (extras de Intent). */
public class Ride {

    private final String id;
    private final String status;
    private final Double agreedFare;
    private final String driverName;
    private final Double driverRating;
    private final String vehicleBrand;
    private final String vehicleModel;
    private final String vehicleColor;
    private final String vehiclePlate;
    private final String originText;
    private final String destinationText;
    private final Double originLat;
    private final Double originLng;
    private final Double destinationLat;
    private final Double destinationLng;
    /**
     * Trazo de la ruta por calles (origen → paradas → destino) codificado con el algoritmo de
     * polilíneas de Google. <b>Solo sirve para dibujar</b>: la distancia y la tarifa las sigue
     * poniendo el servidor. Puede ser null —Google a veces omite el campo— y entonces el mapa cae
     * a la guía recta; que falte el dibujo nunca invalida el viaje.
     */
    private final String polyline;
    private final String requestedAt;
    /**
     * Hora del servidor en la que el conductor marcó "llegué al punto" (ISO-8601), null antes de
     * eso. Es el ancla del cronómetro de espera de 5 min: se toma de aquí y no del reloj del
     * teléfono para que los dos lados cuenten lo mismo y para que reabrir la pantalla a mitad de
     * la espera no reinicie la cuenta.
     */
    private final String driverArrivedAt;
    /** Solo viene poblado en la respuesta de POST /driver/rides/{id}/complete. */
    private final Double commission;
    /**
     * Lo que el pasajero ofreció al pedirlo. Es la referencia contra la que se leen las ofertas
     * de la subasta ("tu oferta"), y no cambia; el precio cerrado es {@link #agreedFare}.
     */
    private final Double passengerOffer;
    /**
     * Paradas intermedias, en orden. Nunca null — lista vacía cuando no hay ninguna.
     *
     * <p>Sin texto: el nombre de la parada lo resolvió el teléfono del pasajero al elegirla sobre
     * el mapa, y eso no viajó nunca al servidor. Para dibujar la ruta no hace falta.
     */
    private final List<Waypoint> waypoints;
    /**
     * Cuándo vence la búsqueda (ISO-8601). Único reloj de la subasta: las ofertas mueren con ella.
     * Es lo que permite retomar una búsqueda a media cuenta sin reiniciarla.
     */
    private final String searchExpiresAt;
    /**
     * El viaje se cerró antes de llegar al destino, por acuerdo de las dos partes.
     *
     * <p>No es una cancelación: el estado es {@code COMPLETED} y {@link #agreedFare} es la tarifa
     * acordada de siempre. {@link #destinationText} sigue siendo el destino <b>que se pidió</b>;
     * quien lo muestre antepone la marca en vez de cambiarlo.
     */
    private final boolean endedEarly;

    public Ride(String id, String status, Double agreedFare, String driverName, Double driverRating,
                String vehicleBrand, String vehicleModel, String vehicleColor, String vehiclePlate,
                String originText, String destinationText, Double originLat, Double originLng,
                Double destinationLat, Double destinationLng, String polyline, String requestedAt,
                String driverArrivedAt, Double commission,
                Double passengerOffer, List<Waypoint> waypoints, String searchExpiresAt,
                boolean endedEarly) {
        this.id = id;
        this.status = status;
        this.agreedFare = agreedFare;
        this.driverName = driverName;
        this.driverRating = driverRating;
        this.vehicleBrand = vehicleBrand;
        this.vehicleModel = vehicleModel;
        this.vehicleColor = vehicleColor;
        this.vehiclePlate = vehiclePlate;
        this.originText = originText;
        this.destinationText = destinationText;
        this.originLat = originLat;
        this.originLng = originLng;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.polyline = polyline;
        this.requestedAt = requestedAt;
        this.driverArrivedAt = driverArrivedAt;
        this.commission = commission;
        this.passengerOffer = passengerOffer;
        this.waypoints = waypoints != null ? waypoints : Collections.emptyList();
        this.searchExpiresAt = searchExpiresAt;
        this.endedEarly = endedEarly;
    }

    /** Si se cerró antes de llegar al destino. Ver {@link #endedEarly}. */
    public boolean endedEarly() {
        return endedEarly;
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public Double getAgreedFare() {
        return agreedFare;
    }

    public String getDriverName() {
        return driverName;
    }

    public Double getDriverRating() {
        return driverRating;
    }

    public String getVehicleBrand() {
        return vehicleBrand;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public String getVehicleColor() {
        return vehicleColor;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public String getOriginText() {
        return originText;
    }

    public String getDestinationText() {
        return destinationText;
    }

    public Double getOriginLat() {
        return originLat;
    }

    public Double getOriginLng() {
        return originLng;
    }

    public Double getDestinationLat() {
        return destinationLat;
    }

    public Double getDestinationLng() {
        return destinationLng;
    }

    public String getPolyline() {
        return polyline;
    }

    public String getRequestedAt() {
        return requestedAt;
    }

    public String getDriverArrivedAt() {
        return driverArrivedAt;
    }

    public Double getPassengerOffer() {
        return passengerOffer;
    }

    /** Nunca null; vacía cuando el viaje no lleva paradas. */
    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public String getSearchExpiresAt() {
        return searchExpiresAt;
    }

    public Double getCommission() {
        return commission;
    }
}
