package com.bng.drivo.data.model;

/**
 * {@code label} es texto libre tal como lo define el contrato de {@code /favorites}
 * (ej. "Casa", "Trabajo") — no hay enum en el servidor. {@link AddressLabel} se usa solo del
 * lado del cliente para elegir un ícono a partir de este texto.
 */
public class SavedAddress {

    private final String id;
    private String label;
    private String address;
    private double lat;
    private double lng;

    public SavedAddress(String id, String label, String address, double lat, double lng) {
        this.id = id;
        this.label = label;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
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
