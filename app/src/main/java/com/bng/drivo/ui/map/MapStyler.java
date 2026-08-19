package com.bng.drivo.ui.map;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import com.bng.drivo.R;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.MapStyleOptions;

/**
 * Estilo minimalista de marca (JSON local, sin depender de un Map ID en Cloud Console).
 * Sigue el tema claro/oscuro del sistema y deja los POI de negocios visibles como
 * referencia de punto de encuentro; el resto del mapa queda limpio para trazar rutas.
 */
public final class MapStyler {

    // El mapa aparece hasta en 4 pantallas por sesión; cachear evita re-parsear el mismo
    // JSON de estilo cada vez que se abre una.
    private static MapStyleOptions lightStyle;
    private static MapStyleOptions darkStyle;

    private MapStyler() {
    }

    public static void apply(Context context, GoogleMap map) {
        boolean isDarkMode = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        boolean applied = map.setMapStyle(isDarkMode ? darkStyle(context) : lightStyle(context));
        if (!applied) {
            Log.e("MapStyler", "No se pudo aplicar el estilo del mapa");
        }
    }

    private static MapStyleOptions lightStyle(Context context) {
        if (lightStyle == null) {
            lightStyle = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_light);
        }
        return lightStyle;
    }

    private static MapStyleOptions darkStyle(Context context) {
        if (darkStyle == null) {
            darkStyle = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark);
        }
        return darkStyle;
    }
}
