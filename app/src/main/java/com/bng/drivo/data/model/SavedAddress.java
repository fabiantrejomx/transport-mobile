package com.bng.drivo.data.model;

import java.util.UUID;

public class SavedAddress {

    private final String id;
    private AddressLabel label;
    private String address;
    private double lat;
    private double lng;

    public SavedAddress(AddressLabel label, String address, double lat, double lng) {
        this(UUID.randomUUID().toString(), label, address, lat, lng);
    }

    public SavedAddress(String id, AddressLabel label, String address, double lat, double lng) {
        this.id = id;
        this.label = label;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
    }

    public String getId() {
        return id;
    }

    public AddressLabel getLabel() {
        return label;
    }

    public void setLabel(AddressLabel label) {
        this.label = label;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }
}
