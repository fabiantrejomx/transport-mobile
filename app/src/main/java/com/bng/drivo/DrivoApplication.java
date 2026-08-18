package com.bng.drivo;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.android.libraries.places.api.Places;

public class DrivoApplication extends Application {

    private static final String TAG = "DrivoApplication";
    private static final String MAPS_API_KEY_META_DATA = "com.google.android.geo.API_KEY";

    @Override
    public void onCreate() {
        super.onCreate();
        initPlaces();
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
