package com.bng.drivo.data.model;

import androidx.annotation.StringRes;

import com.bng.drivo.R;

/**
 * Por qué un viaje se cerró antes de llegar al destino.
 *
 * <p>Espejo del {@code EarlyEndReason} del servidor: el nombre de la constante <b>es</b> lo que
 * viaja en el contrato, así que renombrar una aquí la rompe. Agregar una es agregar una constante
 * y su texto — el servidor no lleva la lista en un {@code CHECK} de la base justo para eso.
 *
 * <p>Corta a propósito. Se responde con el coche todavía en doble fila y el pasajero bajándose;
 * una lista larga se contesta al azar y deja de significar nada.
 */
public enum EarlyEndReason {

    PASSENGER_REQUEST(R.string.driver_trip_early_end_reason_passenger),
    SAFETY(R.string.driver_trip_early_end_reason_safety),
    VEHICLE_ISSUE(R.string.driver_trip_early_end_reason_vehicle),
    OTHER(R.string.driver_trip_early_end_reason_other);

    @StringRes
    private final int label;

    EarlyEndReason(@StringRes int label) {
        this.label = label;
    }

    @StringRes
    public int getLabel() {
        return label;
    }
}
