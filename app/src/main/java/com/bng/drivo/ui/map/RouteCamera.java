package com.bng.drivo.ui.map;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLngBounds;

/**
 * Encuadra una ruta en el mapa: el primer encuadre es instantáneo y los siguientes animados.
 *
 * <p>La distinción importa. {@code animateCamera} anima <em>desde donde esté la cámara</em>, y en
 * un mapa recién creado eso es la posición por defecto del SDK, no la del usuario: usarlo para el
 * primer encuadre hacía que el mapa arrancara en otro sitio y viajara hasta la ruta. Ya encuadrado,
 * en cambio, animar sí aporta — comunica que el recorrido cambió (una parada nueva, el conductor
 * moviéndose).
 *
 * <p>Una instancia por mapa: guarda si ya encuadró, así que no se comparte entre pantallas.
 */
public class RouteCamera {

    private final int paddingPx;
    private boolean framed;
    private boolean retryScheduled;

    /** @param paddingPx aire alrededor de la ruta, para que los pines no queden pegados al borde. */
    public RouteCamera(int paddingPx) {
        this.paddingPx = paddingPx;
    }

    /**
     * @param mapView vista del SupportMapFragment, solo para reintentar si aún no está medida.
     *                Puede ser null: entonces simplemente no se reintenta.
     */
    public void frame(@NonNull GoogleMap map, @NonNull LatLngBounds bounds, @Nullable View mapView) {
        CameraUpdate update = CameraUpdateFactory.newLatLngBounds(bounds, paddingPx);
        try {
            if (framed) {
                map.animateCamera(update);
            } else {
                map.moveCamera(update);
                framed = true;
            }
        } catch (IllegalStateException mapNotMeasuredYet) {
            // newLatLngBounds necesita el tamaño del mapa: si onMapReady llega antes del layout,
            // se reintenta una sola vez tras el siguiente pase, aún sin animar. Sin reintento el
            // encuadre inicial se perdía en silencio y la cámara se quedaba en la posición por
            // defecto hasta el próximo redibujado — el mismo salto que se quiere evitar.
            if (retryScheduled || mapView == null) {
                return;
            }
            retryScheduled = true;
            mapView.post(() -> {
                if (!framed) {
                    frame(map, bounds, mapView);
                }
            });
        }
    }
}
