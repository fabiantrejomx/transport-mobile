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
    private final Double originLat;
    private final Double originLng;
    private final Double destinationLat;
    private final Double destinationLng;
    private final String requestedAt;
    /** Solo viene poblado en la respuesta de POST /driver/rides/{id}/complete. */
    private final Double commission;

    public Ride(String id, String status, Double agreedFare, String driverName, Double driverRating,
                String vehicleBrand, String vehicleModel, String vehicleColor, String vehiclePlate,
                String originText, String destinationText, Double originLat, Double originLng,
                Double destinationLat, Double destinationLng, String requestedAt, Double commission) {
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
        this.requestedAt = requestedAt;
        this.commission = commission;
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

    public String getRequestedAt() {
        return requestedAt;
    }

    public Double getCommission() {
        return commission;
    }
}
