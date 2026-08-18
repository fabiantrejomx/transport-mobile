package com.bng.drivo.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Acceso compartido a SharedPreferences para las implementaciones Mock de los
 * repositorios (sesión, perfil, direcciones guardadas). Único punto que sabe
 * serializar JSON hacia/desde disco, para no repetirlo en cada repositorio.
 */
public class PrefsHelper {

    private static final String PREFS_NAME = "drivo_prefs";

    private final SharedPreferences prefs;

    public PrefsHelper(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public JSONArray getJsonArray(String key) {
        String raw = prefs.getString(key, null);
        if (raw == null) {
            return null;
        }
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return null;
        }
    }

    public void putJsonArray(String key, JSONArray array) {
        prefs.edit().putString(key, array.toString()).apply();
    }

    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }
}
