package com.bng.drivo.ui.map;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bng.drivo.R;
import com.bng.drivo.data.model.NearbyUnit;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.TripRepository;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pinta sobre el mapa del pasajero las unidades disponibles cerca ({@code GET /nearby-drivers}),
 * con el mismo sprite de coche que el conductor ve de sí mismo y que el pasajero ve del conductor
 * asignado — ver {@link MarkerIconFactory#carMarker}.
 *
 * <p><b>Es una foto, no un radar.</b> El contrato no difunde en vivo la posición de los conductores
 * libres: esto es una consulta cada {@link #POLL_INTERVAL_MS}, con puntos redondeados a una celda
 * de 150 m, sin identidad de ningún tipo y con hasta minuto y medio de antigüedad. De ahí salen
 * tres consecuencias que explican casi todo lo que hace esta clase:
 *
 * <ol>
 *   <li><b>No hay rumbo.</b> La respuesta no trae {@code heading}, así que el coche no puede
 *       orientarse con datos reales — ver {@link #decorativeHeading}.</li>
 *   <li><b>No hay identidad.</b> La unidad que llega primera en una respuesta no es necesariamente
 *       la que llegó primera en la anterior, así que las posiciones se <em>saltan</em>: animar el
 *       recorrido entre dos respuestas inventaría el movimiento de un coche a otro distinto.</li>
 *   <li><b>Vacío es una respuesta normal.</b> No es un error, y la regla del contrato es una sola:
 *       lista vacía, estado de texto — nunca un mapa vacío, que comunica "aquí no hay nadie" mucho
 *       más fuerte que una frase. De eso se encarga el host a través de {@link StatusListener}.</li>
 * </ol>
 *
 * <p>El sondeo corre en todo el flujo del pasajero, la subasta incluida: mientras espera ofertas
 * sigue siendo útil ver qué unidades hay alrededor del punto de recogida. Solo pide que las tres
 * condiciones se cumplan a la vez: mapa listo, host visible y punto de consulta conocido.
 * Cualquiera que cambie pasa por {@link #syncPolling()}, que es el único sitio que arranca y para
 * el ciclo.
 */
public class NearbyDriversPresenter {

    /** Cadencia que fija el contrato para esta ruta: mientras la pantalla esté visible, cada 15 s. */
    private static final long POLL_INTERVAL_MS = 15_000L;
    /**
     * Cuánto tiene que moverse el punto de consulta para no esperar al siguiente tic. El servidor
     * redondea las posiciones a una celda de 150 m: por debajo de eso la respuesta sería la misma
     * y adelantarla solo gastaría una llamada.
     */
    private static final float ANCHOR_REFRESH_DISTANCE_M = 150f;

    /** Lo único que el host necesita saber para decidir si enseña el estado de texto. */
    public enum Status {
        /** Todavía no hay respuesta, o el sondeo está apagado: no se afirma nada. */
        UNKNOWN,
        UNITS_NEARBY,
        NONE_NEARBY
    }

    public interface StatusListener {
        void onNearbyStatusChanged(Status status);
    }

    private final Context context;
    private final TripRepository tripRepository;
    private final StatusListener statusListener;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final List<Marker> markers = new ArrayList<>();

    @Nullable
    private GoogleMap map;
    /** Se rasteriza una sola vez y lo comparten los tres marcadores: el bitmap es idéntico. */
    @Nullable
    private BitmapDescriptor carIcon;
    /** Punto alrededor del que se pregunta; null mientras no se conoce la ubicación real. */
    @Nullable
    private LatLng anchor;
    /** No null exactamente mientras el ciclo está vivo — ver {@link #startPolling()}. */
    @Nullable
    private Runnable pollTick;

    private boolean hostStarted;
    private Status status = Status.UNKNOWN;

    /**
     * @param context el de la Activity, no el de aplicación: los colores del coche tienen variante
     *                en values-night y {@code AppCompatDelegate} solo reescribe la configuración de
     *                la Activity (mismo motivo que en {@link DriverRoutePainter}).
     */
    public NearbyDriversPresenter(@NonNull Context context, @NonNull TripRepository tripRepository,
                                   @NonNull StatusListener statusListener) {
        this.context = context;
        this.tripRepository = tripRepository;
        this.statusListener = statusListener;
    }

    public void attach(@NonNull GoogleMap map) {
        this.map = map;
        syncPolling();
    }

    public void detach() {
        stopPolling();
        clearMarkers();
        map = null;
        carIcon = null;
    }

    /**
     * Punto alrededor del que se pregunta: la ubicación real del pasajero en el inicio, y el origen
     * ya elegido en cuanto lo hay. Mientras nadie lo fije no se consulta nada — sin ubicación no se
     * puede afirmar que no haya unidades cerca, y un "no hay nadie" falso es peor que no decir nada.
     */
    public void setAnchor(@NonNull LatLng point) {
        boolean movedEnough = anchor == null
                || distanceMeters(anchor, point) >= ANCHOR_REFRESH_DISTANCE_M;
        anchor = point;
        if (movedEnough) {
            // El tic pendiente preguntaría por el punto viejo; se tira y syncPolling vuelve a
            // preguntar de inmediato por el nuevo.
            stopPolling();
        }
        syncPolling();
    }

    /**
     * Visibilidad real del host. No basta con el ciclo de vida del Fragment: Inicio/Viajes/Ajustes
     * se alternan con show/hide sobre los mismos Fragment ya creados, y eso no dispara onStop —
     * sin esto, el mapa de Inicio seguiría consultando desde otra pestaña. Ver
     * {@code HomeFragment.onHiddenChanged}.
     */
    public void onHostStart() {
        hostStarted = true;
        syncPolling();
    }

    public void onHostStop() {
        hostStarted = false;
        // Los marcadores se quedan: al volver, el primer tic sale de inmediato y sustituirlos
        // en ese ida y vuelta se vería como un parpadeo. Ya nacen con hasta minuto y medio de
        // antigüedad por contrato, así que un rato más no cambia lo que significan.
        syncPolling();
    }

    // ------------------------------------------------------------------------------- Sondeo

    private boolean shouldPoll() {
        return map != null && hostStarted && anchor != null;
    }

    private void syncPolling() {
        if (shouldPoll()) {
            startPolling();
        } else {
            stopPolling();
        }
    }

    private void startPolling() {
        if (pollTick != null) {
            return;
        }
        pollTick = this::poll;
        pollHandler.post(pollTick);
    }

    private void stopPolling() {
        pollHandler.removeCallbacksAndMessages(null);
        pollTick = null;
    }

    private void poll() {
        LatLng point = anchor;
        if (!shouldPoll() || point == null) {
            return;
        }
        tripRepository.getNearbyDrivers(point.latitude, point.longitude, new ApiCallback<List<NearbyUnit>>() {
            @Override
            public void onSuccess(List<NearbyUnit> units) {
                drawUnits(units);
                scheduleNextPoll();
            }

            @Override
            public void onError(ApiException error) {
                // A propósito no se borra nada ni se pasa a NONE_NEARBY: un fallo de red no es
                // "no hay unidades cerca". Decirlo sería afirmar algo que no sabemos, y la foto
                // anterior sigue siendo la mejor respuesta que tenemos hasta el siguiente tic.
                scheduleNextPoll();
            }
        });
    }

    private void scheduleNextPoll() {
        // El siguiente tic se agenda desde la respuesta y no por temporizador fijo: con la red
        // lenta, encadenarlos por tiempo dejaría varias consultas en vuelo pisándose entre ellas.
        if (pollTick == null || !shouldPoll()) {
            return;
        }
        pollHandler.postDelayed(pollTick, POLL_INTERVAL_MS);
    }

    // ---------------------------------------------------------------------------- Marcadores

    private void drawUnits(@Nullable List<NearbyUnit> units) {
        if (map == null) {
            return;
        }
        List<NearbyUnit> incoming = units != null ? units : Collections.emptyList();

        // Los Marker se reutilizan en vez de borrarse y volverse a crear: recrearlos en cada tic
        // los hace parpadear. Se mueven de golpe, sin animar — ver el javadoc de la clase.
        int placed = 0;
        for (NearbyUnit unit : incoming) {
            LatLng position = new LatLng(unit.getLat(), unit.getLng());
            Marker marker;
            if (placed < markers.size()) {
                marker = markers.get(placed);
            } else {
                marker = map.addMarker(carMarkerOptions(position));
                if (marker == null) {
                    continue;
                }
                markers.add(marker);
            }
            marker.setPosition(position);
            marker.setRotation(decorativeHeading(position));
            placed++;
        }
        while (markers.size() > placed) {
            markers.remove(markers.size() - 1).remove();
        }

        setStatus(incoming.isEmpty() ? Status.NONE_NEARBY : Status.UNITS_NEARBY);
    }

    private MarkerOptions carMarkerOptions(LatLng position) {
        if (carIcon == null) {
            carIcon = MarkerIconFactory.carMarker(context, R.color.drivo_vehicle_body);
        }
        return new MarkerOptions()
                .position(position)
                .icon(carIcon)
                .anchor(0.5f, 0.5f)
                // flat: gira con el mapa, como cualquier vehículo dibujado sobre la calzada.
                .flat(true);
    }

    private void clearMarkers() {
        for (Marker marker : markers) {
            marker.remove();
        }
        markers.clear();
    }

    private void setStatus(Status next) {
        if (status == next) {
            return;
        }
        status = next;
        statusListener.onNearbyStatusChanged(next);
    }

    /**
     * Rumbo <b>decorativo</b>: la respuesta no trae ninguno, y no se puede deducir del movimiento
     * entre consultas porque las unidades no traen identidad (la de un tic no es la misma que la
     * del anterior) y las posiciones vienen pegadas a una rejilla de 150 m.
     *
     * <p>Es una función de la posición, no un número al azar: así una unidad que no se mueve se
     * queda apuntando al mismo lado tic tras tic en vez de girar sobre sí misma cada 15 s. La
     * alternativa —dejar el rumbo en 0— alinea los tres coches mirando al norte, que se lee como
     * un error de dibujado antes que como tráfico.
     */
    private static float decorativeHeading(LatLng position) {
        long seed = Double.doubleToLongBits(position.latitude) * 31L
                + Double.doubleToLongBits(position.longitude);
        // Mezcla estilo splitmix64: sin ella, celdas contiguas dan ángulos contiguos y las tres
        // unidades acaban apuntando casi al mismo lado.
        seed ^= (seed >>> 33);
        seed *= 0xff51afd7ed558ccdL;
        seed ^= (seed >>> 33);
        return Math.floorMod(seed, 360L);
    }

    private static float distanceMeters(LatLng from, LatLng to) {
        float[] result = new float[1];
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, result);
        return result[0];
    }
}
