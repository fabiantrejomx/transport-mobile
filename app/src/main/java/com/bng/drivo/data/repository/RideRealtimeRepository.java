package com.bng.drivo.data.repository;

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
        void onDriverLocationChanged(double lat, double lng);
    }

    /** rides/{rideId}/offers — registrar en onStart, detener con el RealtimeSubscription en onStop. */
    RealtimeSubscription observeOffers(String rideId, OffersListener listener);

    /** rides/{rideId}.status — único campo que 6c necesita del documento del viaje. */
    RealtimeSubscription observeRideStatus(String rideId, RideStatusListener listener);

    /** trips/{rideId}/live/driver — un solo doc sobreescrito cada ~5s, no un historial. */
    RealtimeSubscription observeDriverLocation(String rideId, DriverLocationListener listener);
}
