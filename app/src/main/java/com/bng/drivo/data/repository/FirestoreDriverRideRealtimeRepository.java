package com.bng.drivo.data.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirestoreDriverRideRealtimeRepository implements DriverRideRealtimeRepository {

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    @Override
    public RealtimeSubscription observeInbox(String uid, InboxListener listener) {
        com.google.firebase.firestore.ListenerRegistration registration = firestore
                .collection("drivers").document(uid).collection("inbox")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
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
