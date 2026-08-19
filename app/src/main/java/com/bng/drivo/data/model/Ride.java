package com.bng.drivo.data.model;

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
    private final String requestedAt;

    public Ride(String id, String status, Double agreedFare, String driverName, Double driverRating,
                String vehicleBrand, String vehicleModel, String vehicleColor, String vehiclePlate,
                String originText, String destinationText, String requestedAt) {
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
        this.requestedAt = requestedAt;
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

    public String getRequestedAt() {
        return requestedAt;
    }
}
