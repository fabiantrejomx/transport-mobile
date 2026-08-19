package com.bng.drivo.util;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/** Persiste y aplica la preferencia de tema (sistema/claro/oscuro) vía AppCompatDelegate. */
public final class ThemePreferences {

    private static final String KEY_THEME_MODE = "theme_mode";

    public enum Mode {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES);

        final int nightMode;

        Mode(int nightMode) {
            this.nightMode = nightMode;
        }
    }

    private ThemePreferences() {
    }

    public static Mode getMode(Context context) {
        String raw = new PrefsHelper(context).getString(KEY_THEME_MODE, Mode.SYSTEM.name());
        try {
            return Mode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Mode.SYSTEM;
        }
    }

    public static void setMode(Context context, Mode mode) {
        new PrefsHelper(context).putString(KEY_THEME_MODE, mode.name());
        AppCompatDelegate.setDefaultNightMode(mode.nightMode);
    }

    /** Llamar en Application.onCreate(), antes de que se infle cualquier Activity. */
    public static void applyStoredMode(Context context) {
        AppCompatDelegate.setDefaultNightMode(getMode(context).nightMode);
    }
}
