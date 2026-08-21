package com.bng.drivo.data.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementación sobre {@link ConnectivityManager}: el sistema avisa por callback cuando la red
 * aparece o se pierde, así que no hay sondeo ni temporizadores — coste de batería y datos nulo.
 *
 * <p>Se exige {@code NET_CAPABILITY_VALIDATED} además de {@code NET_CAPABILITY_INTERNET}, y esa
 * distinción es justo la que arregla el caso reportado: VALIDATED significa que el propio sistema
 * ya comprobó que hay salida real a internet (incluido el chequeo de portal cautivo), no
 * meramente que el WiFi esté asociado. Con solo INTERNET se avisaría de "hay red" en cuanto el
 * WiFi engancha, todavía sin conexión útil, y el reintento volvería a fallar.
 */
public class SystemConnectivityRepository implements ConnectivityRepository {

    private final ConnectivityManager connectivityManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SystemConnectivityRepository(Context context) {
        connectivityManager = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean isOnline() {
        if (connectivityManager == null) {
            // Sin el servicio no se puede saber; se asume que sí hay red para no bloquear cargas
            // que quizá funcionen. Un fallo real ya se maneja por la vía del error de la petición.
            return true;
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    @Override
    public RealtimeSubscription observe(Listener listener) {
        if (connectivityManager == null) {
            return () -> {
            };
        }

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();

        ValidatedNetworkCallback callback = new ValidatedNetworkCallback(listener);
        connectivityManager.registerNetworkCallback(request, callback);
        // El callback solo reporta transiciones, así que el estado de partida se emite aparte:
        // si la app arranca ya sin red, no habría ningún onAvailable que disparara el aviso.
        callback.emit(isOnline());

        return new RealtimeSubscription() {
            private boolean stopped;

            @Override
            public void stop() {
                // unregisterNetworkCallback lanza IllegalArgumentException si se repite.
                if (stopped) {
                    return;
                }
                stopped = true;
                connectivityManager.unregisterNetworkCallback(callback);
            }
        };
    }

    /**
     * Lleva la cuenta de las redes validadas vivas en vez de mirar solo la última: un traspaso de
     * WiFi a datos móviles emite onLost de una y onAvailable de la otra, y tratar ese onLost como
     * "sin conexión" produciría un parpadeo del aviso aunque nunca se perdiera el acceso.
     */
    private final class ValidatedNetworkCallback extends ConnectivityManager.NetworkCallback {

        private final Listener listener;
        private final Set<Network> validatedNetworks = new HashSet<>();
        private Boolean lastNotified;

        ValidatedNetworkCallback(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            synchronized (validatedNetworks) {
                validatedNetworks.add(network);
            }
            emit(true);
        }

        @Override
        public void onLost(@NonNull Network network) {
            boolean stillOnline;
            synchronized (validatedNetworks) {
                validatedNetworks.remove(network);
                stillOnline = !validatedNetworks.isEmpty();
            }
            emit(stillOnline);
        }

        /**
         * Notifica solo si el estado cambió. Sincronizado porque los callbacks del sistema llegan
         * en un hilo de binder mientras que la emisión inicial ocurre en el principal.
         */
        void emit(boolean online) {
            synchronized (this) {
                if (lastNotified != null && lastNotified == online) {
                    return;
                }
                lastNotified = online;
            }
            mainHandler.post(() -> listener.onConnectivityChanged(online));
        }
    }
}
