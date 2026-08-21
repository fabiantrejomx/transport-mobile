package com.bng.drivo.ui.destination;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bng.drivo.R;
import com.bng.drivo.ui.map.MapPresenter;
import com.bng.drivo.util.GeocoderHelper;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

/**
 * Paso "elige un punto en el mapa": pin fijo al centro, el mapa se arrastra debajo (patrón
 * estándar de apps de transporte). La dirección se resuelve con el Geocoder nativo de Android
 * (best-effort, sin costo ni dependencia nueva).
 *
 * <p>Era una Activity propia. El mapa es el mismo compartido del home — este panel
 * no lo crea ni lo estiliza, solo lee su cámara y reacciona a que se mueva. Los listeners de
 * cámara del SDK son de único destinatario ({@code setOnCameraIdleListener} reemplaza cualquier
 * listener previo), así que {@link com.bng.drivo.ui.home.HomeFragment} es quien los registra una
 * vez y delega aquí solo mientras este paso está activo — ver {@link #onCameraMoveStarted} y
 * {@link #onCameraIdle}.
 */
public class PickLocationPanel {

    public interface Callbacks {
        void onLocationConfirmed(@NonNull String address, double lat, double lng);
    }

    private final View panel;
    private final View pin;
    private final MapPresenter mapPresenter;
    private final Callbacks callbacks;
    private final TextView textSelectedAddress;

    @Nullable
    private String lastResolvedAddress;
    private boolean active;

    public PickLocationPanel(@NonNull View panel, @NonNull View pin, @NonNull MapPresenter mapPresenter,
                             @NonNull Callbacks callbacks) {
        this.panel = panel;
        this.pin = pin;
        this.mapPresenter = mapPresenter;
        this.callbacks = callbacks;

        textSelectedAddress = panel.findViewById(R.id.text_selected_address);
        panel.findViewById(R.id.btn_confirm_location).setOnClickListener(v -> confirmSelectedLocation());
    }

    /** Entrada al paso: no mueve la cámara — sigue exactamente donde el usuario la dejó en Home. */
    public void show() {
        active = true;
        resolveAddressPreview();
    }

    public void hide() {
        active = false;
    }

    /** Llamado por el host desde su único {@code OnCameraMoveStartedListener} del mapa. */
    public void onCameraMoveStarted() {
        if (!active) {
            return;
        }
        lastResolvedAddress = null;
        textSelectedAddress.setText(R.string.pick_location_resolving_address);
    }

    /** Llamado por el host desde su único {@code OnCameraIdleListener} del mapa. */
    public void onCameraIdle() {
        if (active) {
            resolveAddressPreview();
        }
    }

    private void resolveAddressPreview() {
        GoogleMap map = mapPresenter.getMap();
        if (map == null) {
            return;
        }
        LatLng target = map.getCameraPosition().target;
        GeocoderHelper.reverseGeocodeAsync(panel.getContext(), target, address -> {
            if (!active) {
                return;
            }
            lastResolvedAddress = address != null ? address : panel.getContext().getString(
                    R.string.pick_location_fallback_address);
            textSelectedAddress.setText(lastResolvedAddress);
        });
    }

    private void confirmSelectedLocation() {
        GoogleMap map = mapPresenter.getMap();
        if (map == null) {
            return;
        }
        LatLng target = map.getCameraPosition().target;
        if (lastResolvedAddress != null) {
            callbacks.onLocationConfirmed(lastResolvedAddress, target.latitude, target.longitude);
        } else {
            GeocoderHelper.reverseGeocodeAsync(panel.getContext(), target, address -> {
                String resolved = address != null ? address : panel.getContext().getString(
                        R.string.pick_location_fallback_address);
                callbacks.onLocationConfirmed(resolved, target.latitude, target.longitude);
            });
        }
    }
}
