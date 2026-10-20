package com.bng.drivo.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.Process;

import androidx.annotation.NonNull;

/**
 * Datos móviles + Wi-Fi que gastó la app desde que el conductor se conectó, para el aviso
 * permanente de {@link DriverOnlineService}.
 *
 * <p>Sale de {@link TrafficStats} para el uid de la app: no pide permisos, pero cuenta <b>todo</b>
 * el tráfico de Drivo (mapa, fotos, push), no solo el reporte de posición — que es justo lo que le
 * importa al conductor, "cuánto me está costando estar conectado". Los contadores del sistema son
 * acumulados desde el arranque del teléfono, así que se guarda la última lectura y se va sumando
 * la diferencia: sobrevive a que Android mate el proceso y a un reinicio del teléfono (el contador
 * vuelve a cero y la diferencia es la lectura entera).
 *
 * <p>Una "sesión" va de conectarse a desconectarse; los viajes intermedios cuentan dentro de ella.
 */
public final class DriverDataUsage {

    /** Algunos fabricantes no exponen los contadores por uid. */
    public static final long UNSUPPORTED = -1L;

    private static final String PREFS = "driver_data_usage";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_LAST_RAW = "last_raw";
    private static final String KEY_TOTAL = "total";

    private DriverDataUsage() {}

    /** Arranca de cero. Se llama al conectarse desde el botón, no en cada arranque del servicio. */
    public static void startNewSession(@NonNull Context context) {
        long raw = rawBytes();
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, raw != UNSUPPORTED)
                .putLong(KEY_LAST_RAW, raw)
                .putLong(KEY_TOTAL, 0L)
                .apply();
    }

    public static void endSession(@NonNull Context context) {
        prefs(context).edit().clear().apply();
    }

    /**
     * Bytes de la sesión en curso, o {@link #UNSUPPORTED}. Si no había sesión (p. ej. el servicio
     * volvió solo tras matar el proceso con datos ya borrados), empieza una aquí.
     */
    public static long sample(@NonNull Context context) {
        long raw = rawBytes();
        if (raw == UNSUPPORTED) {
            return UNSUPPORTED;
        }
        SharedPreferences prefs = prefs(context);
        if (!prefs.getBoolean(KEY_ACTIVE, false)) {
            startNewSession(context);
            return 0L;
        }
        long lastRaw = prefs.getLong(KEY_LAST_RAW, raw);
        long delta = raw >= lastRaw ? raw - lastRaw : raw;   // raw < lastRaw: el teléfono se reinició
        long total = prefs.getLong(KEY_TOTAL, 0L) + delta;
        prefs.edit().putLong(KEY_LAST_RAW, raw).putLong(KEY_TOTAL, total).apply();
        return total;
    }

    private static long rawBytes() {
        int uid = Process.myUid();
        long rx = TrafficStats.getUidRxBytes(uid);
        long tx = TrafficStats.getUidTxBytes(uid);
        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
            return UNSUPPORTED;
        }
        return rx + tx;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
