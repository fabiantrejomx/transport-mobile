package com.bng.drivo.data.repository;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

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
                    List<String> rideIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        rideIds.add(doc.getId());
                    }
                    listener.onInboxChanged(rideIds);
                });
        return registration::remove;
    }
}
