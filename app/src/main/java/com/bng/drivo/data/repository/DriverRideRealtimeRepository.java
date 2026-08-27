package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.InboxEntry;

import java.util.List;

/** Canal en vivo de Firestore del lado conductor (solo lectura). */
public interface DriverRideRealtimeRepository {

    interface InboxListener {
        void onInboxChanged(List<InboxEntry> entries);
    }

    /** drivers/{uid}/inbox/{rideId} — un documento aparece al entrar al radar, desaparece al salir. */
    RealtimeSubscription observeInbox(String uid, InboxListener listener);
}
