package com.bng.drivo.data.model;

import androidx.annotation.Nullable;

/**
 * Una fila de la bandeja del conductor, leída de {@code drivers/{uid}/inbox/{rideId}}.
 *
 * <p>Antes el canal solo devolvía los ids: la app pedía el detalle por HTTP y recordaba en memoria
 * en cuáles ya había ofertado. Eso no sobrevive a que Android recicle el proceso — al volver, los
 * banners de "oferta enviada" habrían desaparecido con las ofertas todavía vivas en el servidor.
 * Ahora {@code my_offer} viene en el propio documento y el estado es del servidor, no del teléfono.
 *
 * <p>Trae lo justo para pintar un banner sin pedir nada más. El detalle completo (pasajero, ruta,
 * incrementos de contraoferta) sigue viniendo de {@code GET /driver/rides/{id}} al abrirla.
 */
public class InboxEntry {

    private final String rideId;
    @Nullable
    private final String dropoffText;
    private final double passengerOffer;
    /** Lo que este conductor ya ofertó por este viaje, o null si todavía no se ha postulado. */
    @Nullable
    private final Double myOffer;
    /** Fin del plazo, el mismo que ve el pasajero — la subasta corre con un solo reloj. */
    @Nullable
    private final Long expiresAtMillis;

    public InboxEntry(String rideId, @Nullable String dropoffText, double passengerOffer,
                      @Nullable Double myOffer, @Nullable Long expiresAtMillis) {
        this.rideId = rideId;
        this.dropoffText = dropoffText;
        this.passengerOffer = passengerOffer;
        this.myOffer = myOffer;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getRideId() {
        return rideId;
    }

    @Nullable
    public String getDropoffText() {
        return dropoffText;
    }

    public double getPassengerOffer() {
        return passengerOffer;
    }

    @Nullable
    public Double getMyOffer() {
        return myOffer;
    }

    public boolean hasOffered() {
        return myOffer != null;
    }

    @Nullable
    public Long getExpiresAtMillis() {
        return expiresAtMillis;
    }
}
