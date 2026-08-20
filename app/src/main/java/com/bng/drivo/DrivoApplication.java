package com.bng.drivo;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bng.drivo.util.NotificationChannels;
import com.bng.drivo.util.StatusBarStyler;
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
        registerActivityLifecycleCallbacks(new StatusBarLifecycleCallbacks());
    }

    /** Aplica StatusBarStyler a cada Activity creada, sin tener que llamarlo pantalla por
     * pantalla — ver StatusBarStyler para el porqué. */
    private static class StatusBarLifecycleCallbacks implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            StatusBarStyler.apply(activity);
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
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
