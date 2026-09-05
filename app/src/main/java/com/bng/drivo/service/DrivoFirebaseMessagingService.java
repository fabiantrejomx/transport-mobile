package com.bng.drivo.service;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.bng.drivo.R;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DeviceRepository;
import com.bng.drivo.data.repository.RestDeviceRepository;
import com.bng.drivo.ui.driver.DriverActiveTripActivity;
import com.bng.drivo.ui.driver.DriverHomeActivity;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.util.NotificationChannels;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Random;

/**
 * Maneja los 5 tipos de {@code data.type} del contrato a mano (nunca deja que el sistema pinte
 * la notificación solo): {@code new_ride} llega sin bloque {@code notification} a propósito
 * (la app decide cómo alertar), y los demás también se procesan aquí para que el deep-link por
 * {@code data.ride_id} sea uniforme sin importar si la app está en primer o segundo plano.
 *
 * <p>{@code application_reviewed} es el único que no habla de un viaje: no trae {@code ride_id},
 * solo {@code status}. Abre el inicio del conductor, que vuelve a pedir
 * {@code GET /driver/application} y pinta el estado real —incluido el motivo del rechazo, que no
 * viaja en el push justamente porque un push puede llegar tarde y no puede ser la verdad.
 */
public class DrivoFirebaseMessagingService extends FirebaseMessagingService {

    public static final String EXTRA_RIDE_ID = "extra_ride_id";

    private static final String TYPE_NEW_RIDE = "new_ride";
    private static final String TYPE_OFFER_ACCEPTED = "offer_accepted";
    private static final String TYPE_RIDE_TAKEN = "ride_taken";
    private static final String TYPE_RIDE_STATUS = "ride_status";
    private static final String TYPE_APPLICATION_REVIEWED = "application_reviewed";

    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        DeviceRepository deviceRepository = new RestDeviceRepository(getApplicationContext());
        deviceRepository.registerDevice(token, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // no-op
            }

            @Override
            public void onError(ApiException error) {
                // Se reintentará en el siguiente arranque logueado (ver HomeActivity).
            }
        });
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        String type = data.get("type");
        String rideId = data.get("ride_id");
        String status = data.get("status");
        if (type == null) {
            return;
        }

        RemoteMessage.Notification payload = remoteMessage.getNotification();
        String title = payload != null && payload.getTitle() != null
                ? payload.getTitle() : defaultTitleFor(type, status);
        String body = payload != null && payload.getBody() != null
                ? payload.getBody() : defaultBodyFor(type, status);

        showNotification(type, rideId, title, body);
    }

    private void showNotification(String type, String rideId, String title, String body) {
        String channelId;
        if (TYPE_APPLICATION_REVIEWED.equals(type)) {
            channelId = NotificationChannels.ACCOUNT;
        } else if (TYPE_NEW_RIDE.equals(type) || TYPE_OFFER_ACCEPTED.equals(type)) {
            channelId = NotificationChannels.RIDES_HIGH;
        } else {
            channelId = NotificationChannels.RIDES_NORMAL;
        }

        // offer_accepted es la ÚNICA forma en que el conductor se entera de que ganó un
        // viaje (el contrato no tiene "GET mi viaje activo") — por eso se manda directo a
        // DriverActiveTripActivity en vez de a HomeActivity como el resto de los tipos.
        //
        // application_reviewed va al inicio del conductor: es la pantalla que ya sabe pintar
        // los cinco estados del expediente, y los vuelve a consultar al abrirse.
        Class<?> destination;
        if (TYPE_OFFER_ACCEPTED.equals(type)) {
            destination = DriverActiveTripActivity.class;
        } else if (TYPE_APPLICATION_REVIEWED.equals(type)) {
            destination = DriverHomeActivity.class;
        } else {
            destination = HomeActivity.class;
        }
        // EXTRA_RIDE_ID coincide en texto con DriverActiveTripActivity.EXTRA_RIDE_ID —
        // un solo putExtra sirve para los dos destinos posibles.
        Intent intent = new Intent(this, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_RIDE_ID, rideId);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, new Random().nextInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_map_pin)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        // Sin viaje, el aviso se identifica por su tipo: así un segundo veredicto reemplaza al
        // primero en la bandeja en vez de acumularse, y no choca con las notificaciones de viaje.
        int notificationId = rideId != null ? rideId.hashCode() : type.hashCode();
        NotificationManagerCompat.from(this).notify(notificationId, notification.build());
    }

    private String defaultTitleFor(String type, String status) {
        if (TYPE_APPLICATION_REVIEWED.equals(type)) {
            if (STATUS_APPROVED.equals(status)) {
                return getString(R.string.notif_application_approved_title);
            }
            if (STATUS_REJECTED.equals(status)) {
                return getString(R.string.notif_application_rejected_title);
            }
            return getString(R.string.notif_application_reviewed_title);
        }
        switch (type) {
            case TYPE_NEW_RIDE:
                return getString(R.string.notif_new_ride_title);
            case TYPE_OFFER_ACCEPTED:
                return getString(R.string.notif_offer_accepted_title);
            case TYPE_RIDE_TAKEN:
                return getString(R.string.notif_ride_taken_title);
            case TYPE_RIDE_STATUS:
                return getString(R.string.notif_ride_status_title);
            default:
                return getString(R.string.app_name);
        }
    }

    private String defaultBodyFor(String type, String status) {
        if (TYPE_APPLICATION_REVIEWED.equals(type)) {
            if (STATUS_APPROVED.equals(status)) {
                return getString(R.string.notif_application_approved_body);
            }
            if (STATUS_REJECTED.equals(status)) {
                return getString(R.string.notif_application_rejected_body);
            }
            return getString(R.string.notif_application_reviewed_body);
        }
        switch (type) {
            case TYPE_NEW_RIDE:
                return getString(R.string.notif_new_ride_body);
            case TYPE_OFFER_ACCEPTED:
                return getString(R.string.notif_offer_accepted_body);
            case TYPE_RIDE_TAKEN:
                return getString(R.string.notif_ride_taken_body);
            case TYPE_RIDE_STATUS:
                return getString(R.string.notif_ride_status_body);
            default:
                return "";
        }
    }
}
