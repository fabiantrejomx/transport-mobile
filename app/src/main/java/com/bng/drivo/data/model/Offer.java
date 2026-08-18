package com.bng.drivo.data.model;

/** Una tarjeta del radar, leída en vivo de Firestore rides/{id}/offers/{offerId}. */
public class Offer {

    private final String offerId;
    private final String driverName;
    private final Double driverRating;
    private final String vehicleBrand;
    private final String vehicleModel;
    private final String vehicleColor;
    private final String vehiclePlate;
    private final double amount;
    private final Integer etaMin;
    private final int queuePosition;
    private final int queueTotal;

    public Offer(String offerId, String driverName, Double driverRating, String vehicleBrand,
                 String vehicleModel, String vehicleColor, String vehiclePlate, double amount, Integer etaMin,
                 int queuePosition, int queueTotal) {
        this.offerId = offerId;
        this.driverName = driverName;
        this.driverRating = driverRating;
        this.vehicleBrand = vehicleBrand;
        this.vehicleModel = vehicleModel;
        this.vehicleColor = vehicleColor;
        this.vehiclePlate = vehiclePlate;
        this.amount = amount;
        this.etaMin = etaMin;
        this.queuePosition = queuePosition;
        this.queueTotal = queueTotal;
    }

    public String getOfferId() {
        return offerId;
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

    public double getAmount() {
        return amount;
    }

    public Integer getEtaMin() {
        return etaMin;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public int getQueueTotal() {
        return queueTotal;
    }
}
