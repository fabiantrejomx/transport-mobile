package com.bng.drivo.data.repository;

/** Handle para cancelar un listener de tiempo real, sin filtrar el tipo de Firestore hacia arriba. */
public interface RealtimeSubscription {
    void stop();
}
