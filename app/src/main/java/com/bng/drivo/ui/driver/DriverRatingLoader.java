package com.bng.drivo.ui.driver;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DriverRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Busca el promedio de estrellas del propio conductor rebuscando en sus viajes.
 *
 * <p><b>Esto es un puente y hay que borrarlo.</b> El sitio donde este número debería vivir es
 * {@code GET /me}, que las dos apps ya llaman para el nombre; en cuanto el backend lo exponga ahí,
 * esta clase y sus dos llamadas sobran (ver {@code MeDto#rating}). Mientras tanto, el único punto
 * del contrato que devuelve la calificación de un conductor es el bloque {@code driver} de
 * {@code GET /rides/{id}}, así que hay que pedir el historial y luego el detalle de algún viaje
 * solo para leer tres caracteres.
 *
 * <p>Y es un promedio de verdad, no las estrellas de ese viaje concreto: el campo es decimal (4.9)
 * mientras que el {@code stars} que se envía al calificar es un entero del 1 al 5 — un número con
 * decimales solo puede salir de una media. Por eso da igual de qué viaje se lea.
 *
 * <p>Que dé igual cuál no significa que cualquiera sirva: un viaje cancelado antes de asignarse o
 * expirado sin conductor no responde con ese bloque, y preguntando solo por el más reciente la
 * calificación salía en blanco aunque hubiera decenas de viajes calificados detrás. De ahí las dos
 * defensas: primero los COMPLETED, que seguro tuvieron conductor, y si uno no da número se prueba
 * con el siguiente hasta {@link #LOOKUP_ATTEMPTS}.
 */
final class DriverRatingLoader {

    private static final String TAG = "DriverRating";

    /** Suficientes para saltarse un par de viajes raros seguidos, y techo a las llamadas: sin él,
     *  un conductor con 50 viajes y la red caída dispararía 50 peticiones encadenadas. */
    private static final int LOOKUP_ATTEMPTS = 3;

    /** Cuántos viajes se piden del historial: de sobra para encontrar uno con bloque driver. */
    private static final int HISTORY_LIMIT = 50;

    interface Callback {
        /** @param rating null si ningún viaje consultado lo devolvió; nunca un valor inventado. */
        void onRatingResolved(@Nullable Double rating);
    }

    private DriverRatingLoader() {
    }

    static void load(@NonNull DriverRepository repository, @NonNull Callback callback) {
        repository.getRideHistory(HISTORY_LIMIT, new ApiCallback<List<RideSummary>>() {
            @Override
            public void onSuccess(List<RideSummary> rides) {
                tryNext(repository, candidatesFrom(rides), 0, callback);
            }

            @Override
            public void onError(ApiException error) {
                callback.onRatingResolved(null);
            }
        });
    }

    /** Los COMPLETED primero; el resto detrás, por si el conductor todavía no cerró ninguno. */
    static List<String> candidatesFrom(@NonNull List<RideSummary> rides) {
        List<String> candidates = new ArrayList<>();
        for (RideSummary ride : rides) {
            if ("COMPLETED".equals(ride.getStatus())) {
                candidates.add(ride.getId());
            }
        }
        for (RideSummary ride : rides) {
            if (!"COMPLETED".equals(ride.getStatus())) {
                candidates.add(ride.getId());
            }
        }
        return candidates;
    }

    private static void tryNext(DriverRepository repository, List<String> rideIds, int index,
                                Callback callback) {
        if (index >= rideIds.size() || index >= LOOKUP_ATTEMPTS) {
            callback.onRatingResolved(null);
            return;
        }
        repository.getRideDetail(rideIds.get(index), new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (ride.getDriverRating() == null) {
                    tryNext(repository, rideIds, index + 1, callback);
                    return;
                }
                callback.onRatingResolved(ride.getDriverRating());
            }

            @Override
            public void onError(ApiException error) {
                Log.w(TAG, "No se pudo leer la calificación del viaje " + rideIds.get(index)
                        + ": " + error.getCode(), error);
                tryNext(repository, rideIds, index + 1, callback);
            }
        });
    }
}
