package com.bng.drivo.data.repository;

/**
 * Estado de conectividad del dispositivo, como fuente de eventos en vez de algo que se consulta
 * en bucle.
 *
 * <p>Interfaz aparte de su implementación por la misma razón que el resto de repositorios (ver
 * CLAUDE.md): al portar a iOS el equivalente es {@code NWPathMonitor}, y la lógica de pantalla
 * no tiene por qué enterarse de cuál de los dos hay debajo.
 */
public interface ConnectivityRepository {

    interface Listener {
        /** Se invoca siempre en el hilo principal, y solo cuando el estado cambia de verdad. */
        void onConnectivityChanged(boolean online);
    }

    /**
     * @return true si ahora mismo hay salida real a internet, no solo una red asociada.
     */
    boolean isOnline();

    /**
     * Empieza a observar. El listener recibe el estado actual de entrada y luego cada cambio.
     *
     * @return handle que hay que parar (típicamente en onStop) para no dejar el callback vivo.
     */
    RealtimeSubscription observe(Listener listener);
}
