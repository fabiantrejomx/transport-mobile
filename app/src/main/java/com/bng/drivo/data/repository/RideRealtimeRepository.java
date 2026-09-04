package com.bng.drivo.data.repository;

import androidx.annotation.Nullable;

import com.bng.drivo.data.model.Offer;

import java.util.List;

/** Canal en vivo de Firestore (solo lectura — toda escritura pasa por transport-api). */
public interface RideRealtimeRepository {

    interface OffersListener {
        void onOffersChanged(List<Offer> offers);
    }

    interface RideStatusListener {
        void onStatusChanged(String status);
    }

    interface DriverLocationListener {
        /**
         * @param etaMin minutos que le faltan al conductor para llegar a donde toca según la fase
         *               —al punto de recogida antes de IN_PROGRESS, al destino a partir de ahí—.
         *               Lo calcula el servidor para que el conductor y el pasajero vean el mismo
         *               número. Null si no vino: entonces no se enseñan minutos, que es mejor que
         *               enseñar unos inventados.
         */
        void onDriverLocationChanged(double lat, double lng, @Nullable Integer etaMin);
    }

    /** rides/{rideId}/offers — registrar en onStart, detener con el RealtimeSubscription en onStop. */
    RealtimeSubscription observeOffers(String rideId, OffersListener listener);

    /** rides/{rideId}.status — único campo que 6c necesita del documento del viaje. */
    RealtimeSubscription observeRideStatus(String rideId, RideStatusListener listener);

    /**
     * trips/{rideId}/live/driver — un solo doc sobreescrito cada ~5s, no un historial.
     *
     * <p>Lo leen los dos participantes: el pasajero para ver por dónde viene el coche, y el
     * conductor —que ya sabe dónde está— para el ETA, que es del servidor y no suyo.
     */
    RealtimeSubscription observeDriverLocation(String rideId, DriverLocationListener listener);
}
