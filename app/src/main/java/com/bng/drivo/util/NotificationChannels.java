package com.bng.drivo.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.bng.drivo.R;

/**
 * Canales de FCM del contrato de transport-api: {@code new_ride}/{@code offer_accepted} son
 * "Alta" prioridad (el conductor tiene 20-60 s para reaccionar), {@code ride_taken}/
 * {@code ride_status} son "Normal". Ver la sección "Notificaciones (FCM)" de openapi.yaml.
 *
 * <p>{@code application_reviewed} tiene canal propio y no entra en los de viaje: es de la cuenta,
 * no de una carrera, y va aparte para que el conductor pueda silenciar las actualizaciones de
 * viaje sin perderse el veredicto que lleva días esperando — o al revés.
 */
public final class NotificationChannels {

    public static final String RIDES_HIGH = "rides_high";
    public static final String RIDES_NORMAL = "rides_normal";
    public static final String ACCOUNT = "account_status";

    private NotificationChannels() {
    }

    public static void createAll(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(
                RIDES_HIGH, context.getString(R.string.notif_channel_rides_high), NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(
                RIDES_NORMAL, context.getString(R.string.notif_channel_rides_normal), NotificationManager.IMPORTANCE_DEFAULT));
        // Alta: llega una sola vez, después de días de espera, y decide si el conductor puede
        // trabajar o tiene que corregir su expediente. Merece asomarse sobre lo que esté viendo.
        manager.createNotificationChannel(new NotificationChannel(
                ACCOUNT, context.getString(R.string.notif_channel_account), NotificationManager.IMPORTANCE_HIGH));
    }
}
