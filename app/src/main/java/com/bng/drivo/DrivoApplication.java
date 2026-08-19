package com.bng.drivo;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.bng.drivo.util.NotificationChannels;
import com.bng.drivo.util.ThemePreferences;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.libraries.places.api.Places;

public class DrivoApplication extends Application {

    private static final String TAG = "DrivoApplication";
    private static final String MAPS_API_KEY_META_DATA = "com.google.android.geo.API_KEY";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePreferences.applyStoredMode(this);
        initPlaces();
        initMapsRenderer();
        NotificationChannels.createAll(this);
    }

    /** Pide el renderer nuevo (más rápido) desde el arranque, antes de que cualquier
     * pantalla infle un mapa — evita que la primera pantalla con mapa pague ese costo. */
    private void initMapsRenderer() {
        MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST, renderer ->
                Log.i(TAG, "Maps renderer: " + renderer));
    }

    private void initPlaces() {
        if (Places.isInitialized()) {
            return;
        }
        String apiKey = readApiKeyFromManifest();
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "No se encontró la API key de Maps/Places en el manifest; Places no se inicializó.");
            return;
        }
        Places.initializeWithNewPlacesApiEnabled(this, apiKey);
    }

    private String readApiKeyFromManifest() {
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            return appInfo.metaData != null ? appInfo.metaData.getString(MAPS_API_KEY_META_DATA) : null;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "No se pudo leer el meta-data de la API key", e);
            return null;
        }
    }
}
