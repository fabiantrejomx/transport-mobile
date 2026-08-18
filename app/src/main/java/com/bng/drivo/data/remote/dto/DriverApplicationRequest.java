package com.bng.drivo.data.remote.dto;

public class DriverApplicationRequest {
    public String modality;
    public String curp;
    public String rfc;
    public Vehicle vehicle;

    public DriverApplicationRequest(String modality, String curp, String rfc, Vehicle vehicle) {
        this.modality = modality;
        this.curp = curp;
        this.rfc = rfc;
        this.vehicle = vehicle;
    }

    public static class Vehicle {
        public String brand;
        public String model;
        public String color;
        public String plate;
        public int year;
        public boolean is_owner;

        public Vehicle(String brand, String model, String color, String plate, int year, boolean isOwner) {
            this.brand = brand;
            this.model = model;
            this.color = color;
            this.plate = plate;
            this.year = year;
            this.is_owner = isOwner;
        }
    }
}
