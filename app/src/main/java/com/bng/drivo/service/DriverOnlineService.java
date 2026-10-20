package com.bng.drivo.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.bng.drivo.R;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.ui.driver.DriverHomeActivity;
import com.bng.drivo.util.NotificationChannels;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Mantiene al conductor en el radar mientras la app no está en pantalla.
 *
 * <p>Antes el reporte de posición vivía en el {@code onStart}/{@code onStop} de las Activities del
 * conductor, y eso ataba estar disponible a estar mirando el teléfono. Dos consecuencias reales:
 *
 * <ul>
 *   <li><b>En el radar:</b> al minimizar o cerrar la app dejaba de reportar, y el barredor del
 *       servidor lo sacaba del radar a los 5 minutos ({@code STALE_MINUTES}). Durante esos cinco
 *       minutos seguía recibiendo solicitudes que no podía ver ni contestar.</li>
 *   <li><b>Con un viaje encima:</b> el coche se congelaba en el mapa del pasajero justo cuando ese
 *       mapa es lo único que le dice por dónde viene su conductor.</li>
 * </ul>
 *
 * <p>Es la misma pieza que corren Uber, DiDi e InDrive, y por eso llevan un aviso permanente: la
 * disponibilidad de un conductor no puede depender de qué app tenga abierta. El aviso no es un
 * peaje, es la contrapartida — mientras esté ahí, el teléfono está reportando.
 *
 * <p><b>Sobrevive a que se cierre la app desde recientes</b> ({@code stopWithTask} por omisión es
 * false), que es exactamente el punto: cerrar la pantalla no es desconectarse. Para desconectarse
 * se toca "Desconectarme" en el aviso (ver {@link DriverGoOfflineReceiver}), o se abre el inicio
 * del conductor donde vive el botón. Solo en el radar: con un viaje encima no se ofrece.
 *
 * <p>El aviso también dice cuántos datos lleva gastados la app desde que se conectó
 * ({@link DriverDataUsage}), que es la duda con la que un conductor apaga la app para ahorrar.
 *
 * <p>El ritmo de envío es el mismo de siempre ({@code POST /driver/location} cada 12 s): esto
 * cambia <b>cuándo</b> se reporta, no cuánto.
 */
public class DriverOnlineService extends Service {

    private static final int NOTIFICATION_ID = 4021;

    private static final String ACTION_ONLINE = "com.bng.drivo.action.DRIVER_ONLINE";
    private static final String ACTION_TRIP = "com.bng.drivo.action.DRIVER_TRIP";

    /** Lo que el mapa necesita para que el coche se mueva en vez de dar saltos. */
    private static final long LOCATION_INTERVAL_MS = 5000L;
    /**
     * Cada cuánto se le manda la posición al servidor. Más espaciado que las lecturas a propósito:
     * acelerar el dibujo no es razón para triplicar el tráfico ni el consumo de datos.
     */
    private static final long REPORT_INTERVAL_MS = 12000L;

    private DriverRepository driverRepository;
    private FusedLocationProviderClient fusedLocationClient;
    @Nullable
    private LocationCallback locationCallback;
    private long lastReportAt;
    /** Último texto de datos pintado: el aviso solo se repinta cuando cambia. */
    @Nullable
    private String lastUsageText;
    @Nullable
    private String mode;

    /**
     * En el radar, esperando solicitudes.
     *
     * <p>Idempotente: volver a llamarla con el servicio ya corriendo solo repinta el aviso.
     */
    public static void startOnline(@NonNull Context context) {
        start(context, ACTION_ONLINE);
    }

    /** Con un viaje asignado. Mismo servicio, otro texto — y no se apaga al desconectarse. */
    public static void startTrip(@NonNull Context context) {
        start(context, ACTION_TRIP);
    }

    /** Fin de la sesión: el contador de datos vuelve a cero para la siguiente conexión. */
    public static void stop(@NonNull Context context) {
        context.stopService(new Intent(context, DriverOnlineService.class));
        DriverDataUsage.endSession(context);
    }

    private static void start(Context context, String action) {
        // Sin permiso de ubicación, arrancar un servicio de tipo "location" es una excepción en
        // Android 14+. Y sin ubicación no habría nada que reportar, así que no hay nada que perder.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent intent = new Intent(context, DriverOnlineService.class).setAction(action);
        ContextCompat.startForegroundService(context, intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        driverRepository = new RestDriverRepository(getApplicationContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // Un reinicio del sistema tras matar el proceso llega con intent null: se cae al radar,
        // que es el estado que de verdad depende de seguir reportando.
        mode = intent != null && intent.getAction() != null ? intent.getAction() : ACTION_ONLINE;
        lastUsageText = usageText();
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
        startLocationLoop();
        // START_STICKY y no START_NOT_STICKY: si Android mata el proceso por memoria mientras el
        // conductor sigue conectado en el servidor, lo correcto es volver, no quedarse fuera del
        // radar en silencio.
        return START_STICKY;
    }

    /** Solo se arranca, nunca se enlaza. */
    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopLocationLoop();
        super.onDestroy();
    }

    private Notification buildNotification() {
        boolean enViaje = ACTION_TRIP.equals(mode);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, abrirLaApp(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body = getString(enViaje
                ? R.string.driver_service_trip_body : R.string.driver_service_online_body);
        if (lastUsageText != null) {
            body = getString(R.string.driver_service_data_usage_format, body, lastUsageText);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationChannels.DRIVER_SERVICE)
                .setSmallIcon(R.drawable.ic_map_pin)
                .setContentTitle(getString(enViaje
                        ? R.string.driver_service_trip_title : R.string.driver_service_online_title))
                .setContentText(body)
                .setContentIntent(contentIntent)
                // No se descarta ni se agrupa como "reciente": describe un estado, no un evento.
                .setOngoing(true)
                .setShowWhen(false)
                // Se repinta con cada cambio del contador de datos: que no vuelva a sonar ni vibrar.
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        // Desconectarse con un viaje encima no aplica: el viaje sigue y hay que cerrarlo.
        if (!enViaje) {
            PendingIntent goOffline = PendingIntent.getBroadcast(this, 0,
                    new Intent(this, DriverGoOfflineReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_close, getString(R.string.driver_service_go_offline_action),
                    goOffline);
        }
        return builder.build();
    }

    /** Null si el teléfono no expone los contadores: el aviso queda como antes. */
    @Nullable
    private String usageText() {
        long bytes = DriverDataUsage.sample(this);
        return bytes == DriverDataUsage.UNSUPPORTED ? null : Formatter.formatShortFileSize(this, bytes);
    }

    /**
     * Repinta el aviso si el contador cambió. Va al ritmo del reporte (12 s) y no con un
     * temporizador propio: no hace falta despertar el teléfono solo para esto.
     */
    private void refreshUsage() {
        String text = usageText();
        if (text == null || text.equals(lastUsageText)) {
            return;
        }
        lastUsageText = text;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    /**
     * Tocar el aviso vuelve a la app tal como estaba, igual que tocar su icono.
     *
     * <p>Y no {@code DriverHomeActivity} con {@code CLEAR_TOP}, que era lo obvio: eso se lleva por
     * delante la pantalla del viaje: justo la que el conductor está mirando cuando el aviso dice
     * "viaje en curso". El intent del lanzador trae {@code RESET_TASK_IF_NEEDED}, que trae la
     * tarea al frente sin recrear nada; y con la app cerrada arranca por el Splash, que ya sabe
     * enrutar a un conductor con viaje abierto.
     */
    private Intent abrirLaApp() {
        Intent lanzador = getPackageManager().getLaunchIntentForPackage(getPackageName());
        return lanzador != null ? lanzador
                : new Intent(this, DriverHomeActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @SuppressLint("MissingPermission")
    private void startLocationLoop() {
        if (locationCallback != null) {
            return;
        }
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS).build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) {
                    report(location);
                }
            }
        };
        fusedLocationClient.requestLocationUpdates(request, locationCallback, getMainLooper());
    }

    private void stopLocationLoop() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    /**
     * El filtro por tiempo es del cliente, no del servidor: {@code POST /driver/location} rechaza
     * envíos con menos de 3 s de separación ({@code TOO_MANY_PINGS}), y gastarse el rechazo es
     * pagar datos por un error.
     */
    private void report(Location location) {
        long now = System.currentTimeMillis();
        if (now - lastReportAt < REPORT_INTERVAL_MS) {
            return;
        }
        lastReportAt = now;
        refreshUsage();
        driverRepository.reportLocation(location.getLatitude(), location.getLongitude(),
                location.hasBearing() ? (double) location.getBearing() : null,
                location.hasAccuracy() ? (double) location.getAccuracy() : null,
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        // no-op
                    }

                    @Override
                    public void onError(ApiException error) {
                        // Un envío perdido no cambia nada: el siguiente va en 12 s, y el margen
                        // del barredor son 5 minutos.
                    }
                });
    }
}
