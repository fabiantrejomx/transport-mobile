package com.bng.drivo.util;

import android.app.Activity;
import android.content.res.Configuration;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * El tema base (Theme.Material3.DayNight.NoActionBar) no declara {@code windowLightStatusBar}
 * en ningún values/values-night propio de esta app, así que el color de los íconos de la barra
 * de estado terminaba siguiendo el modo oscuro/claro del SISTEMA en vez del que el usuario
 * eligió a mano (ThemePreferences → AppCompatDelegate.setDefaultNightMode). Se fija explícito
 * aquí, leyendo el modo YA RESUELTO de esta Activity ({@code Configuration.uiMode}, que
 * AppCompatDelegate ya actualizó tras el cambio manual) — nunca el del sistema directo.
 *
 * <p>Registrado como {@link android.app.Application.ActivityLifecycleCallbacks} en
 * DrivoApplication para que aplique a cada Activity sin tener que llamarlo pantalla por
 * pantalla.
 */
public final class StatusBarStyler {

    private StatusBarStyler() {
    }

    public static void apply(Activity activity) {
        boolean isDarkMode = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!isDarkMode);
        controller.setAppearanceLightNavigationBars(!isDarkMode);
    }
}
