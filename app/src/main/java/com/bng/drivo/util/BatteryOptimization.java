package com.bng.drivo.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;

/**
 * Pedirle al conductor que deje la batería de Drivo "Sin restricciones".
 *
 * <p>Existe por una prueba real: conectado, con la app en segundo plano y el aviso permanente de
 * {@code DriverOnlineService} a la vista, a los pocos minutos el sistema mató el proceso entero y
 * el servidor lo sacó del radar. Un servicio en primer plano basta en Android puro, pero el ahorro
 * de batería de varios fabricantes (Samsung, Xiaomi, Huawei, Oppo, Motorola…) lo ignora. Es la
 * misma petición que hacen las apps de conductor de Uber, DiDi e InDrive.
 *
 * <p>No insiste: si el conductor dice "Ahora no", no se vuelve a preguntar en {@link #SNOOZE_MS}.
 */
public final class BatteryOptimization {

    private static final String PREFS = "battery_optimization";
    private static final String KEY_SNOOZED_UNTIL = "snoozed_until";
    private static final long SNOOZE_MS = 24L * 60 * 60 * 1000;

    private BatteryOptimization() {}

    public static boolean isExempt(@NonNull Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        return power == null || power.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /** Sí, si la app sigue optimizada y no se pospuso hace menos de un día. */
    public static boolean shouldAsk(@NonNull Context context) {
        if (isExempt(context)) {
            return false;
        }
        long until = prefs(context).getLong(KEY_SNOOZED_UNTIL, 0L);
        return System.currentTimeMillis() >= until;
    }

    public static void snooze(@NonNull Context context) {
        prefs(context).edit()
                .putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
                .apply();
    }

    /**
     * Abre el diálogo del sistema "¿Permitir que Drivo se ejecute en segundo plano?" (un toque).
     * Si el fabricante lo quitó, cae a la lista de optimización de batería y, en último caso, a la
     * ficha de la app, donde está el ajuste de batería.
     */
    @SuppressLint("BatteryLife") // Uso aceptado: la función principal del conductor depende de ello.
    public static void requestExemption(@NonNull Activity activity) {
        String pkg = activity.getPackageName();
        Intent[] intents = {
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg)),
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)),
        };
        for (Intent intent : intents) {
            try {
                activity.startActivity(intent);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Siguiente opción.
            }
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
