package com.bng.drivo.data.remote.dto;

public class RideDto {
    public String id;
    public String status;
    public DriverSummaryDto driver;
    public Double agreed_fare;
    public PlaceDto origin;
    public PlaceDto destination;
    public String requested_at;
    public String driver_arrived_at;
    public String completed_at;
    /** Solo viene poblado en la respuesta de POST /driver/rides/{id}/complete. */
    public Double commission;
}
