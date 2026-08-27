package com.bng.drivo.data.repository;

import android.util.Log;

import com.bng.drivo.data.model.InboxEntry;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FirestoreDriverRideRealtimeRepository implements DriverRideRealtimeRepository {

    private static final String TAG = "DriverRealtime";

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    @Override
    public RealtimeSubscription observeInbox(String uid, InboxListener listener) {
        com.google.firebase.firestore.ListenerRegistration registration = firestore
                .collection("drivers").document(uid).collection("inbox")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        // Sin esto, una regla denegada se ve igual que "no hay solicitudes".
                        Log.w(TAG, "El canal en vivo de la bandeja falló: " + error.getCode(), error);
                        return;
                    }
                    if (snapshot == null) {
                        return;
                    }
                    List<InboxEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        entries.add(toEntry(doc));
                    }
                    listener.onInboxChanged(entries);
                });
        return registration::remove;
    }

    @SuppressWarnings("unchecked")
    private InboxEntry toEntry(QueryDocumentSnapshot doc) {
        Map<String, Object> dropoff = (Map<String, Object>) doc.get("dropoff");
        String dropoffText = dropoff != null ? (String) dropoff.get("text") : null;

        // my_offer solo existe si este conductor ya se postuló a este viaje: es lo que
        // distingue un banner de "oferta enviada" de una solicitud por decidir.
        Map<String, Object> myOffer = (Map<String, Object>) doc.get("my_offer");
        Double amount = myOffer != null ? asDouble(myOffer.get("amount")) : null;

        Timestamp expiresAt = doc.getTimestamp("expires_at");
        Double offer = asDouble(doc.get("offer"));

        return new InboxEntry(doc.getId(), dropoffText, offer != null ? offer : 0, amount,
                expiresAt != null ? expiresAt.toDate().getTime() : null);
    }

    private Double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }
}
