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
import com.bng.drivo.ui.auth.RoleSelectionActivity;
import com.bng.drivo.ui.driver.DriverActiveTripActivity;
import com.bng.drivo.ui.driver.DriverHomeActivity;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.ui.trip.ActiveTripActivity;
import com.bng.drivo.util.LiveRadar;
import com.bng.drivo.util.NotificationChannels;
import com.bng.drivo.util.PrefsHelper;
import com.bng.drivo.util.VisibleScreen;
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

        if (loAtiendeLaPantallaQueSeVe(type, rideId)) {
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

        Class<?> destination = destinationFor(type);
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

    /**
     * Si el aviso ya lo está atendiendo, en vivo, la pantalla que el usuario tiene delante.
     *
     * <p>La notificación es para cuando la app <b>no</b> está a la vista. Con el radar del
     * conductor delante, una solicitud entrante se abre sola en el modal y suena con
     * {@link com.bng.drivo.util.RideAlert} (tono del sistema + vibración, que es lo que de verdad
     * avisa a alguien que va manejando). Pintar además una notificación no cuenta nada nuevo y sí
     * añade una salida de la pantalla en la que hay que decidir.
     *
     * <p>Los tres avisos del conductor <b>se le entregan</b> al radar en vez de darlo por hecho:
     * {@link LiveRadar} devuelve si se hizo cargo, y solo entonces se calla la notificación. Antes
     * bastaba con que la pantalla estuviera visible, lo que daba por buena una entrega que dependía
     * de la bandeja de Firestore; el día que el canal en vivo tardó en levantar, las solicitudes no
     * aparecieron ni en el modal ni en la barra. Ver el javadoc de {@code LiveRadar}: no se
     * silencia un aviso que nadie recibió.
     *
     * <p>{@code application_reviewed} queda fuera a propósito: no hay ninguna pantalla que lo
     * refresque sola —el inicio del conductor solo vuelve a consultar el expediente al volver del
     * segundo plano—, así que si se silenciara con la app abierta el veredicto no aparecería por
     * ningún lado.
     */
    private boolean loAtiendeLaPantallaQueSeVe(String type, String rideId) {
        switch (type) {
            case TYPE_NEW_RIDE:
                return LiveRadar.deliverNewRide(rideId);
            case TYPE_RIDE_TAKEN:
                return LiveRadar.deliverRideTaken(rideId);
            case TYPE_OFFER_ACCEPTED:
                return LiveRadar.deliverOfferAccepted();
            case TYPE_RIDE_STATUS:
                // El estado del viaje lo sigue el listener de rides/{id} en la pantalla del viaje,
                // la del pasajero o la del conductor según de quién sea este teléfono.
                return VisibleScreen.isShowing(ActiveTripActivity.class)
                        || VisibleScreen.isShowing(DriverActiveTripActivity.class);
            default:
                return false;
        }
    }

    /**
     * A qué pantalla lleva cada tipo de aviso.
     *
     * <p>Se decide <b>por el destinatario del push</b>, no por descarte. Antes todo lo que no
     * fuera {@code offer_accepted} ni {@code application_reviewed} caía en un {@code else} que
     * abría {@link HomeActivity} — el inicio del <em>pasajero</em>. Como {@code new_ride} y
     * {@code ride_taken} son avisos del conductor (los manda el servidor a su {@code driverId},
     * ver FcmPush), tocar la notificación de una solicitud entrante sacaba al conductor de su app
     * y lo dejaba en la del pasajero, con el viaje perdido.
     *
     * <p>Pasaba desapercibido mientras cerrar la app dejaba al conductor fuera del radar en
     * minutos: casi nunca llegaba un {@code new_ride} que no estuviera ya en pantalla. Con el
     * servicio en primer plano el conductor sigue disponible con la app guardada, así que ahora
     * este es el camino normal y tiene que llevar a donde debe.
     *
     * <p>{@code ride_status} es el único que puede ser para cualquiera de los dos lados: le llega
     * al pasajero cuando el conductor cancela o cierra el viaje, y al conductor cuando el pasajero
     * cancela. Como el push no dice de quién es, lo resuelve el modo en el que está este teléfono
     * — que es justo lo que {@code PREF_KEY_DRIVER_MODE} sabe.
     */
    private Class<?> destinationFor(String type) {
        switch (type) {
            case TYPE_OFFER_ACCEPTED:
                // El atajo para enterarse de que ganó el viaje. GET /driver/current-ride lo
                // respalda si el push no llega o no se toca.
                return DriverActiveTripActivity.class;
            case TYPE_NEW_RIDE:
            case TYPE_RIDE_TAKEN:
            case TYPE_APPLICATION_REVIEWED:
                return DriverHomeActivity.class;
            case TYPE_RIDE_STATUS:
            default:
                return enModoConductor() ? DriverHomeActivity.class : HomeActivity.class;
        }
    }

    private boolean enModoConductor() {
        return new PrefsHelper(this)
                .getBoolean(RoleSelectionActivity.PREF_KEY_DRIVER_MODE, false);
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
