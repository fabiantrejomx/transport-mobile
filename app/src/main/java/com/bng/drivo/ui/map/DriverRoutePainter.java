package com.bng.drivo.ui.map;

import android.content.Context;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bng.drivo.R;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dibuja sobre el mapa del conductor los dos tramos que tiene un viaje desde su punto de vista:
 *
 * <ol>
 *   <li><b>Tramo de recogida</b> (azul, del conductor al pasajero): es el trayecto que le toca
 *       manejar antes de empezar a cobrar.</li>
 *   <li><b>Tramo del viaje</b> (verde, del origen al destino del pasajero, pasando por sus
 *       paradas intermedias si las pidió): es el viaje que el pasajero pidió, en el mismo color
 *       que el pasajero ve en su propia app.</li>
 * </ol>
 *
 * <p>El azul sale de {@code drivo_map_accent} y no de {@code drivo_primary}: el mapa no es una
 * superficie del tema, así que un color pensado para contrastar contra {@code colorSurface} no
 * sirve ahí — el azul marino de marca desaparecía sobre el mapa en tema oscuro.
 *
 * <p>Al llegar una solicitud se muestran los dos a la vez, que es lo que el conductor necesita
 * para decidir (qué tan lejos está el pasajero y qué tan largo es el viaje). Ya aceptado, se
 * enseña uno a la vez según la fase: recogida hasta que arranca el viaje, y el viaje del pasajero
 * a partir de ahí — ver DriverActiveTripActivity.
 *
 * <p>Los dos tramos no se dibujan igual, y la diferencia viene del servidor:
 *
 * <ul>
 *   <li>El <b>tramo del viaje</b> se pinta por calles cuando la API manda su {@code polyline} —el
 *       trazo que ya calculó y pagó—, con la guía punteada recta de respaldo si no llega.</li>
 *   <li>El <b>tramo de recogida</b> es siempre guía recta, y así se queda: el servidor no lo
 *       calcula con Google en absoluto (su ETA sale de la línea recta por un factor de calle), y
 *       pedirlo costaría una llamada de pago por cada conductor de cada oleada.</li>
 * </ul>
 *
 * <p>El cliente sigue sin calcular recorrido, aquí solo se desempaqueta el dibujo ajeno. No usa
 * {@code map.clear()} para no llevarse por delante nada más que haya en el mapa.
 */
public class DriverRoutePainter {

    private static final int MARKER_DIAMETER_DP = 16;
    private static final float ROUTE_WIDTH_PX = 8f;
    /** La ruta real, más gruesa: es un trazo por calles y no debe perderse entre ellas. */
    private static final float REAL_ROUTE_WIDTH_PX = 12f;
    /** Aire alrededor del encuadre; el hueco libre real lo aporta el padding del mapa. */
    private static final int FRAME_PADDING_PX = 90;

    private final Context context;
    private final RouteCamera camera = new RouteCamera(FRAME_PADDING_PX);
    private final List<Marker> markers = new ArrayList<>();
    private final List<Polyline> polylines = new ArrayList<>();

    @Nullable
    private GoogleMap map;
    @Nullable
    private View mapView;
    /** Se guarda aparte de {@link #markers} para poder moverlo sin repintar todo. */
    @Nullable
    private Marker driverMarker;
    /**
     * Con la cámara inclinada el coche visto desde arriba se ve en escorzo y se lee como una
     * mancha; en esa vista se cambia por la flecha de navegación. Ver
     * {@link MarkerIconFactory#navigationPuck}.
     */
    private boolean navigationMode;
    /**
     * Último rumbo conocido, en grados. Se guarda aquí y no solo en el marcador porque el marcador
     * se destruye y se vuelve a crear en cada repintado —cambio de fase, cambio de alto del modal—
     * y sin esto el coche volvería a apuntar al norte cada vez.
     */
    @Nullable
    private Double driverBearing;
    /** Últimos puntos encuadrados, para poder rehacer el encuadre si cambia el hueco visible. */
    private final List<LatLng> framedPoints = new ArrayList<>();

    /**
     * Guarda el contexto que le pasan (el de la Activity), no el de aplicación: los colores del
     * coche tienen variante en values-night, y el selector Sistema/Claro/Oscuro se aplica con
     * {@code AppCompatDelegate}, que reescribe la configuración de la Activity pero no la del
     * contexto de aplicación — con este último, un tema forzado a mano se pintaría con los colores
     * del otro. No hay fuga: quien lo construye es la Activity y lo guarda como campo suyo.
     */
    public DriverRoutePainter(Context context) {
        this.context = context;
    }

    public void attach(@NonNull GoogleMap map, @Nullable View mapView) {
        this.map = map;
        this.mapView = mapView;
    }

    public void detach() {
        map = null;
        mapView = null;
        markers.clear();
        polylines.clear();
        framedPoints.clear();
        driverMarker = null;
    }

    /**
     * Rehace el encuadre con los mismos puntos. Lo llama el host cuando cambia el alto del modal:
     * el encuadre se calcula contra el padding del mapa, y si la ruta se dibujó antes de que el
     * modal terminara de medirse, quedaría medio tapada por él.
     */
    public void reframe() {
        frame(new ArrayList<>(framedPoints));
    }

    public boolean isReady() {
        return map != null;
    }

    /** Ver {@link RouteCamera#markPositioned()}: la pantalla ya centró el mapa por su cuenta. */
    public void markCameraPositioned() {
        camera.markPositioned();
    }

    /**
     * Solicitud entrante: los dos tramos a la vez, encuadrando todo lo que hay.
     *
     * <p>El del viaje se pinta por calles cuando la solicitud trae su trazo, porque es lo que el
     * conductor está decidiendo: una recta entre dos puntos no dice por dónde tendría que manejar
     * ni cuánto se le va a atravesar la ciudad. El de recogida sigue recto.
     */
    public void showRequestPreview(@Nullable LatLng driver, @NonNull LatLng pickup,
                                    @NonNull List<LatLng> stops, @Nullable LatLng dropoff,
                                    @Nullable String encodedPolyline) {
        clear();
        if (map == null) {
            return;
        }
        List<LatLng> framed = new ArrayList<>();
        addPickupLeg(driver, pickup, framed);
        if (dropoff != null) {
            addTripLeg(pickup, stops, dropoff, encodedPolyline, framed);
        }
        frame(framed);
    }

    /** Fase "voy por el pasajero": solo el tramo de recogida. */
    public void showPickupLeg(@Nullable LatLng driver, @NonNull LatLng pickup) {
        clear();
        if (map == null) {
            return;
        }
        List<LatLng> framed = new ArrayList<>();
        addPickupLeg(driver, pickup, framed);
        frame(framed);
    }

    /**
     * Fase "viaje en curso": el tramo que pidió el pasajero, con sus paradas, más el coche del
     * conductor — visible en todo momento, no solo mientras va por el pasajero. No entra al
     * encuadre a propósito: es el mismo criterio que el resto de este archivo, reencuadrar cada
     * vez que se mueve le quitaría al conductor el control de la cámara.
     */
    /**
     * @param frameRoute falso una vez arrancado el viaje: ahí la cámara la lleva el host, pegada al
     *                   conductor. Encuadrar el recorrido entero servía para decidir si le convenía
     *                   el viaje; ya manejando solo aleja la vista de la calle que tiene delante.
     */
    public void showTripLeg(@Nullable LatLng driver, @NonNull LatLng pickup,
                             @NonNull List<LatLng> stops, @NonNull LatLng dropoff,
                             @Nullable String encodedPolyline, boolean frameRoute) {
        clear();
        if (map == null) {
            return;
        }
        List<LatLng> framed = new ArrayList<>();
        addTripLeg(pickup, stops, dropoff, encodedPolyline, framed);
        if (driver != null) {
            addDriverMarker(driver);
        }
        if (frameRoute) {
            frame(framed);
        }
        // Sin encuadrar no se guardan puntos: así reframe(), que corre cuando el modal cambia de
        // alto, no arrastra la cámara de vuelta al recorrido completo.
    }

    /**
     * Mueve el coche sin reencuadrar: durante el trayecto la posición llega cada pocos segundos y
     * reencuadrar en cada lectura le quitaría al conductor el control de la cámara.
     */
    public void updateDriverPosition(@NonNull LatLng driver) {
        updateDriverPosition(driver, null);
    }

    /**
     * @param bearing rumbo en grados, o null si no se conoce —parado el GPS no lo da, y forzarlo
     *                haría girar el coche con cada temblor de la señal en un semáforo—. Al no
     *                venir se conserva el último, que es la última dirección real que llevaba.
     */
    public void updateDriverPosition(@NonNull LatLng driver, @Nullable Double bearing) {
        if (bearing != null) {
            driverBearing = bearing;
        }
        if (driverMarker != null) {
            driverMarker.setPosition(driver);
            if (bearing != null) {
                driverMarker.setRotation(bearing.floatValue());
            }
        }
    }

    private void addPickupLeg(@Nullable LatLng driver, @NonNull LatLng pickup, List<LatLng> framed) {
        addMarker(pickup, R.color.drivo_success);
        framed.add(pickup);
        if (driver == null) {
            return;
        }
        addDriverMarker(driver);
        framed.add(driver);
        addLine(driver, pickup, R.color.drivo_map_accent);
    }

    /**
     * Cambia entre el coche y la flecha de navegación, y lo recuerda para los repintados.
     *
     * <p>Lo decide el host, que es quien sabe si la cámara está inclinada; el pintor solo obedece.
     */
    public void setNavigationMode(boolean enabled) {
        if (navigationMode == enabled) {
            return;
        }
        navigationMode = enabled;
        if (driverMarker != null) {
            driverMarker.setIcon(driverIcon());
        }
    }

    private void addDriverMarker(LatLng driver) {
        // Plano contra el mapa y girado al rumbo: así el vehículo apunta a donde avanza aunque el
        // conductor haya rotado el mapa, que es de lo que depende que la flecha de navegación
        // signifique algo.
        driverMarker = map.addMarker(new MarkerOptions().position(driver)
                .icon(driverIcon())
                .anchor(0.5f, 0.5f)
                .rotation(driverBearing != null ? driverBearing.floatValue() : 0f)
                .flat(true));
    }

    private com.google.android.gms.maps.model.BitmapDescriptor driverIcon() {
        return navigationMode
                ? MarkerIconFactory.navigationPuck(context, R.color.drivo_vehicle_body)
                : MarkerIconFactory.carMarker(context, R.color.drivo_vehicle_body);
    }

    /**
     * Origen → paradas → destino: la línea pasa por cada parada en orden, no salta directo al
     * destino, y cada parada lleva su propio punto azul (mismo código de color que en la app del
     * pasajero, donde los puntos intermedios ya se pintan así).
     */
    private void addTripLeg(@NonNull LatLng pickup, @NonNull List<LatLng> stops,
                             @NonNull LatLng dropoff, @Nullable String encodedPolyline,
                             List<LatLng> framed) {
        List<LatLng> line = new ArrayList<>();
        line.add(pickup);
        addMarker(pickup, R.color.drivo_success);
        for (LatLng stop : stops) {
            line.add(stop);
            addMarker(stop, R.color.drivo_map_accent);
        }
        line.add(dropoff);
        addMarker(dropoff, R.color.drivo_secondary);

        // Los marcadores se quedan en los puntos que eligió el pasajero: Google pega los extremos
        // de su trazo a la calle más cercana y el punto de encuentro se vería corrido.
        framed.addAll(line);

        List<LatLng> realRoute = PolylineDecoder.decode(encodedPolyline);
        if (realRoute.size() >= 2) {
            framed.addAll(realRoute);
            addRealRoute(realRoute);
        } else {
            addLine(line, R.color.drivo_success);
        }
    }

    /** El recorrido por calles: sólido y más grueso, porque este sí es el camino que se recorre. */
    private void addRealRoute(List<LatLng> points) {
        polylines.add(map.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(REAL_ROUTE_WIDTH_PX)
                .color(context.getColor(R.color.drivo_success))
                .jointType(JointType.ROUND)
                .startCap(new RoundCap())
                .endCap(new RoundCap())));
    }

    private void addMarker(LatLng position, @ColorRes int colorRes) {
        if (map == null) {
            return;
        }
        Marker marker = map.addMarker(new MarkerOptions().position(position)
                .icon(MarkerIconFactory.circle(context, colorRes, MARKER_DIAMETER_DP))
                .anchor(0.5f, 0.5f));
        if (marker != null) {
            markers.add(marker);
        }
    }

    private void addLine(LatLng from, LatLng to, @ColorRes int colorRes) {
        addLine(Arrays.asList(from, to), colorRes);
    }

    private void addLine(List<LatLng> points, @ColorRes int colorRes) {
        if (map == null || points.size() < 2) {
            return;
        }
        List<PatternItem> dashed = Arrays.asList(new Dash(20f), new Gap(12f));
        polylines.add(map.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(ROUTE_WIDTH_PX)
                .color(context.getColor(colorRes))
                .pattern(dashed)));
    }

    private void frame(List<LatLng> points) {
        if (map == null || points.isEmpty()) {
            return;
        }
        if (points != framedPoints) {
            framedPoints.clear();
            framedPoints.addAll(points);
        }
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (LatLng point : points) {
            bounds.include(point);
        }
        camera.frame(map, bounds.build(), mapView);
    }

    public void clear() {
        for (Marker marker : markers) {
            marker.remove();
        }
        markers.clear();
        if (driverMarker != null) {
            driverMarker.remove();
            driverMarker = null;
        }
        for (Polyline polyline : polylines) {
            polyline.remove();
        }
        polylines.clear();
        framedPoints.clear();
    }
}
