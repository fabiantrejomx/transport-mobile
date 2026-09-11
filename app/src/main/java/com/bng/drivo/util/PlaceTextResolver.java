package com.bng.drivo.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.bng.drivo.R;
import com.google.android.gms.maps.model.LatLng;

/**
 * Resuelve el texto de un punto del viaje cuando lo que se guardó es un placeholder de
 * "ubicación actual" en vez de una dirección de verdad.
 *
 * <p>Nació para el <em>conductor</em>: el pasajero manda su origen como "Tu ubicación actual" (es
 * el placeholder de su propia app), y ese texto viaja tal cual hasta la solicitud del conductor.
 * Ahí no significa nada — o peor, se lee como si fuera la ubicación del conductor, que es justo lo
 * contrario. El mismo problema aparece en el historial del <em>propio pasajero</em> abierto desde
 * otro sitio: "Tu ubicación actual" tampoco dice nada si el viaje se hizo en otra parte. En los
 * dos casos, cuando llega ese placeholder (o nada), se geocodifica la coordenada real para mostrar
 * una dirección de verdad.
 */
public final class PlaceTextResolver {

    public interface Callback {
        void onResolved(String text);
    }

    private PlaceTextResolver() {
    }

    public static void resolve(Context context, @Nullable String text, @Nullable LatLng at,
                                Callback callback) {
        resolve(context, text, at, context.getString(R.string.incoming_request_place_unknown), callback);
    }

    /** Igual que {@link #resolve(Context, String, LatLng, Callback)}, pero con un mensaje propio
     * para cuando ni el texto ni la coordenada alcanzan — "Ubicación del pasajero" tiene sentido
     * en la solicitud del conductor, no en el propio historial del pasajero. */
    public static void resolve(Context context, @Nullable String text, @Nullable LatLng at,
                                String unknownFallback, Callback callback) {
        if (!isPlaceholder(context, text)) {
            callback.onResolved(text);
            return;
        }
        if (at == null) {
            callback.onResolved(unknownFallback);
            return;
        }
        GeocoderHelper.reverseGeocodeAsync(context, at, address ->
                callback.onResolved(address != null && !address.isEmpty() ? address : unknownFallback));
    }

    /** Expuesto para quien necesite decidir si vale la pena resolver antes de tener la
     * coordenada a mano (ej. el historial, que solo la trae en el detalle del viaje). */
    public static boolean isPlaceholder(Context context, @Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }
        return text.equalsIgnoreCase(context.getString(R.string.home_origin_placeholder))
                || text.equalsIgnoreCase(context.getString(R.string.incoming_request_pickup_label));
    }
}
