package com.bng.drivo.data.remote.dto;

/**
 * Dónde está el conductor. La misma forma sirve para marcar llegada al punto y para finalizar el
 * viaje: en los dos casos el servidor comprueba por GPS que esté donde dice estar antes de dejarlo
 * avanzar.
 */
public class DriverAtLocationRequest {
    public double lat;
    public double lng;

    public DriverAtLocationRequest(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }
}
