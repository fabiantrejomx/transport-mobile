package com.bng.drivo.util;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

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

    // setStatusBarColor/setNavigationBarColor están @Deprecated desde API 35 porque ahí el
    // sistema ya los ignora y fuerza transparente por su cuenta — la guarda de SDK_INT de abajo
    // es justo lo que hace que llamarlos siga siendo correcto en las versiones que los atienden.
    @SuppressWarnings("deprecation")
    public static void apply(Activity activity) {
        // De borde a borde en TODA versión soportada (minSdk 26), no solo donde el sistema lo
        // obliga. Desde Android 15 (API 35) el propio SO fuerza esto para apps con targetSdk 35+
        // y por eso en el Samsung (Android 16) ya se veía bien sin tocar nada; en Android 10-14
        // nadie lo pedía, así que la ventana se quedaba en el layout clásico: status bar opaca con
        // el color por defecto del tema (no el fondo oscuro de la app) y contenido empujado hacia
        // abajo por el sistema en vez de dibujarse detrás. Pedirlo aquí, para cada Activity, hace
        // que las dos versiones se comporten igual — y en Android 15+ es un no-op, porque ya
        // estaba forzado.
        //
        // Todo el código que ya lee WindowInsetsCompat para acomodar cabeceras y flotantes
        // (DrawerInsets, HomeFragment, DriverHomeActivity, DriverActiveTripActivity,
        // AddEditAddressActivity) fue escrito y probado contra ese modo de borde a borde: sin
        // este cambio, en Android 10-14 esos insets llegaban en 0 porque el sistema ya se había
        // encargado de empujar el contenido por su cuenta, y ese código no tenía nada que sumar.
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        // setDecorFitsSystemWindows(false) por sí solo deja que el contenido se dibuje DETRÁS de
        // las barras, pero no las vuelve transparentes: el tema les seguía pintando su color de
        // fondo por defecto encima de ese contenido, y en Android 10-14 eso se veía como una franja
        // opaca (el primario del tema) en vez de fundirse con la pantalla. Desde API 35 el sistema
        // ya las fuerza a transparentes y estos dos setters quedaron obsoletos — de ahí la guarda:
        // en Android 15+ no hace falta llamarlos, y llamarlos ahí solo dispara una advertencia de
        // API obsoleta por nada.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }

        boolean isDarkMode = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!isDarkMode);
        controller.setAppearanceLightNavigationBars(!isDarkMode);
    }
}
