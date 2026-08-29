package com.bng.drivo.data.remote.dto;

/**
 * Cuerpo de PATCH /favorites/{id}: un cambio parcial, no un reemplazo.
 *
 * <p>Un campo null significa "esto no se toca". Viaja explícito como {@code "campo": null}
 * —{@code ApiClient} usa {@code serializeNulls()}— y el servidor lo lee igual, porque resuelve
 * cada campo con {@code COALESCE(:campo, campo)}. {@code lat} y {@code lng} van los dos o
 * ninguno: medio punto no es un punto y el servidor lo rechaza.
 */
public class FavoritePatchRequest {
    public String label;
    public String address_text;
    public Double lat;
    public Double lng;

    public FavoritePatchRequest(String label, String addressText, Double lat, Double lng) {
        this.label = label;
        this.address_text = addressText;
        this.lat = lat;
        this.lng = lng;
    }
}
