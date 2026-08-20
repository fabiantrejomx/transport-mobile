package com.bng.drivo.data.repository;

import java.util.List;

/** Canal en vivo de Firestore del lado conductor (solo lectura). */
public interface DriverRideRealtimeRepository {

    interface InboxListener {
        void onInboxChanged(List<String> rideIds);
    }

    /** drivers/{uid}/inbox/{rideId} — un documento aparece al entrar al radar, desaparece al salir. */
    RealtimeSubscription observeInbox(String uid, InboxListener listener);
}
