package com.bng.drivo.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.bng.drivo.R;
import com.google.android.gms.maps.model.LatLng;

/**
 * Resuelve el texto de un punto del viaje para mostrárselo al <em>conductor</em>.
 *
 * <p>Existe por un detalle del contrato: el pasajero manda su origen como "Tu ubicación actual"
 * (es el placeholder de su propia app), y ese texto viaja tal cual hasta la solicitud del
 * conductor. Ahí no significa nada — o peor, se lee como si fuera la ubicación del conductor,
 * que es justo lo contrario. Cuando llega ese placeholder (o nada), se geocodifica la coordenada
 * real del pasajero para mostrar una dirección de verdad.
 */
public final class PlaceTextResolver {

    public interface Callback {
        void onResolved(String text);
    }

    private PlaceTextResolver() {
    }

    public static void resolve(Context context, @Nullable String text, @Nullable LatLng at,
                                Callback callback) {
        if (!isPlaceholder(context, text)) {
            callback.onResolved(text);
            return;
        }
        if (at == null) {
            callback.onResolved(context.getString(R.string.incoming_request_place_unknown));
            return;
        }
        GeocoderHelper.reverseGeocodeAsync(context, at, address ->
                callback.onResolved(address != null && !address.isEmpty() ? address
                        : context.getString(R.string.incoming_request_place_unknown)));
    }

    private static boolean isPlaceholder(Context context, @Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }
        return text.equalsIgnoreCase(context.getString(R.string.home_origin_placeholder))
                || text.equalsIgnoreCase(context.getString(R.string.incoming_request_pickup_label));
    }
}
