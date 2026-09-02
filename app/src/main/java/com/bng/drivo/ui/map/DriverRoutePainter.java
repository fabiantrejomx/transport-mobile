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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

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
 * <p>Como en {@link MapPresenter}, las líneas son guías rectas punteadas, no rutas de calles: el
 * contrato es explícito en que el cliente no calcula recorrido. No usa {@code map.clear()} para no
 * llevarse por delante nada más que haya en el mapa.
 */
public class DriverRoutePainter {

    private static final int MARKER_DIAMETER_DP = 16;
    private static final float ROUTE_WIDTH_PX = 8f;
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

    /** Solicitud entrante: los dos tramos a la vez, encuadrando todo lo que hay. */
    public void showRequestPreview(@Nullable LatLng driver, @NonNull LatLng pickup,
                                    @NonNull List<LatLng> stops, @Nullable LatLng dropoff) {
        clear();
        if (map == null) {
            return;
        }
        List<LatLng> framed = new ArrayList<>();
        addPickupLeg(driver, pickup, framed);
        if (dropoff != null) {
            addTripLeg(pickup, stops, dropoff, framed);
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
    public void showTripLeg(@Nullable LatLng driver, @NonNull LatLng pickup,
                             @NonNull List<LatLng> stops, @NonNull LatLng dropoff) {
        clear();
        if (map == null) {
            return;
        }
        List<LatLng> framed = new ArrayList<>();
        addTripLeg(pickup, stops, dropoff, framed);
        if (driver != null) {
            addDriverMarker(driver);
        }
        frame(framed);
    }

    /**
     * Mueve el coche sin reencuadrar: durante el trayecto la posición llega cada pocos segundos y
     * reencuadrar en cada lectura le quitaría al conductor el control de la cámara.
     */
    public void updateDriverPosition(@NonNull LatLng driver) {
        if (driverMarker != null) {
            driverMarker.setPosition(driver);
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

    private void addDriverMarker(LatLng driver) {
        driverMarker = map.addMarker(new MarkerOptions().position(driver)
                .icon(MarkerIconFactory.carMarker(context, R.color.drivo_vehicle_body))
                .anchor(0.5f, 0.5f)
                .flat(true));
    }

    /**
     * Origen → paradas → destino: la línea pasa por cada parada en orden, no salta directo al
     * destino, y cada parada lleva su propio punto azul (mismo código de color que en la app del
     * pasajero, donde los puntos intermedios ya se pintan así).
     */
    private void addTripLeg(@NonNull LatLng pickup, @NonNull List<LatLng> stops,
                             @NonNull LatLng dropoff, List<LatLng> framed) {
        List<LatLng> line = new ArrayList<>();
        line.add(pickup);
        addMarker(pickup, R.color.drivo_success);
        for (LatLng stop : stops) {
            line.add(stop);
            addMarker(stop, R.color.drivo_map_accent);
        }
        line.add(dropoff);
        addMarker(dropoff, R.color.drivo_secondary);

        framed.addAll(line);
        addLine(line, R.color.drivo_success);
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
