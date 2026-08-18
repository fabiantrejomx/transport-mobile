package com.bng.drivo.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Reverse geocoding con el Geocoder nativo de Android (best-effort, sin costo ni
 * dependencia nueva) — ver docs/drivo-analisis-inicial.md para la migración futura
 * a Places API cuando aplique. Compartido por PickLocationOnMapActivity y
 * AddEditAddressActivity para no duplicar el hilo/try-catch en cada pantalla.
 */
public final class GeocoderHelper {

    private static final String TAG = "GeocoderHelper";

    public interface Callback {
        void onResult(String address);
    }

    private GeocoderHelper() {
    }

    public static void reverseGeocodeAsync(Context context, LatLng target, Callback callback) {
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String address = reverseGeocode(appContext, target);
            mainHandler.post(() -> callback.onResult(address));
        }).start();
    }

    private static String reverseGeocode(Context context, LatLng target) {
        if (!Geocoder.isPresent()) {
            return null;
        }
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            @SuppressWarnings("deprecation")
            List<Address> results = geocoder.getFromLocation(target.latitude, target.longitude, 1);
            if (results != null && !results.isEmpty()) {
                return results.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            Log.w(TAG, "No se pudo geocodificar la ubicación", e);
        }
        return null;
    }
}
