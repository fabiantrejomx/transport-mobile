package com.bng.drivo.data.repository;

import android.util.Log;

import com.bng.drivo.data.model.Offer;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FirestoreRideRealtimeRepository implements RideRealtimeRepository {

    private static final String TAG = "RideRealtime";

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    /**
     * Un listener que falla es indistinguible de uno que no recibe nada, y así se perdió una vez
     * la lista entera de ofertas del pasajero: las reglas de Firestore la negaban (faltaba el
     * documento padre rides/{id}) y en pantalla eso se veía igual que "todavía nadie ofertó".
     * Registrarlo no arregla nada por sí solo, pero el fallo deja de ser mudo.
     */
    private static boolean failed(String what, com.google.firebase.firestore.FirebaseFirestoreException error) {
        if (error == null) {
            return false;
        }
        Log.w(TAG, "El canal en vivo de " + what + " falló: " + error.getCode(), error);
        return true;
    }

    @Override
    public RealtimeSubscription observeOffers(String rideId, OffersListener listener) {
        com.google.firebase.firestore.ListenerRegistration registration = firestore
                .collection("rides").document(rideId).collection("offers")
                .orderBy("queue_position")
                .addSnapshotListener((snapshot, error) -> {
                    if (failed("las ofertas del viaje " + rideId, error) || snapshot == null) {
                        return;
                    }
                    List<Offer> offers = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        offers.add(toOffer(doc));
                    }
                    listener.onOffersChanged(offers);
                });
        return registration::remove;
    }

    @SuppressWarnings("unchecked")
    private Offer toOffer(QueryDocumentSnapshot doc) {
        Map<String, Object> driver = (Map<String, Object>) doc.get("driver");
        String name = driver != null ? (String) driver.get("name") : null;
        Double rating = asDouble(driver != null ? driver.get("rating") : null);
        String brand = driver != null ? (String) driver.get("brand") : null;
        String model = driver != null ? (String) driver.get("model") : null;
        String color = driver != null ? (String) driver.get("color") : null;
        String plate = driver != null ? (String) driver.get("plate") : null;

        double amount = asDouble(doc.get("amount")) != null ? asDouble(doc.get("amount")) : 0;
        Long etaMin = doc.getLong("eta_min");
        Long queuePosition = doc.getLong("queue_position");
        Long queueTotal = doc.getLong("queue_total");
        Timestamp expiresAt = doc.getTimestamp("expires_at");

        return new Offer(doc.getId(), name, rating, brand, model, color, plate, amount,
                etaMin != null ? etaMin.intValue() : null,
                queuePosition != null ? queuePosition.intValue() : 1,
                queueTotal != null ? queueTotal.intValue() : 1,
                expiresAt != null ? expiresAt.toDate().getTime() : null);
    }

    private Double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    @Override
    public RealtimeSubscription observeRideStatus(String rideId, RideStatusListener listener) {
        com.google.firebase.firestore.ListenerRegistration registration = firestore
                .collection("rides").document(rideId)
                .addSnapshotListener((snapshot, error) -> {
                    if (failed("el estado del viaje " + rideId, error)
                            || snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    String status = snapshot.getString("status");
                    if (status != null) {
                        listener.onStatusChanged(status);
                    }
                });
        return registration::remove;
    }

    @Override
    public RealtimeSubscription observeDriverLocation(String rideId, DriverLocationListener listener) {
        com.google.firebase.firestore.ListenerRegistration registration = firestore
                .collection("trips").document(rideId).collection("live").document("driver")
                .addSnapshotListener((snapshot, error) -> {
                    if (failed("la posición del conductor en el viaje " + rideId, error)
                            || snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    Double lat = snapshot.getDouble("lat");
                    Double lng = snapshot.getDouble("lng");
                    Long etaMin = snapshot.getLong("eta_min");
                    if (lat != null && lng != null) {
                        listener.onDriverLocationChanged(lat, lng,
                                etaMin != null ? etaMin.intValue() : null);
                    }
                });
        return registration::remove;
    }
}
