package com.bng.drivo.ui.map;

import android.content.Context;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bng.drivo.R;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
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
 * Dueño del único {@link GoogleMap} del flujo del pasajero: dibuja la guía de ruta y mantiene el
 * encuadre dentro de la zona que las tarjetas dejan libre.
 *
 * <p>La pieza que importa es {@link #setContentPadding}. Antes cada pantalla tenía su propio mapa
 * y encuadraba la ruta con un padding fijo adivinado a ojo (220px) sobre la pantalla completa, sin
 * saber cuánto tapaban las tarjetas de arriba y abajo — por eso había que reajustar márgenes cada
 * vez que el contenido crecía. Aquí el mapa recibe el alto real de esas dos zonas, el SDK encuadra
 * contra el rectángulo visible que queda en medio, y la ruta cae siempre centrada en él sin que
 * nadie tenga que medir nada a mano.
 *
 * <p>No usa {@code map.clear()}: el mapa es de larga vida y compartido por los tres pasos, así que
 * los marcadores y la polilínea se quitan uno a uno para no llevarse por delante nada más.
 */
public class MapPresenter {

    /** Aire alrededor de la ruta, ya <em>dentro</em> del área visible — no compensa las tarjetas. */
    private static final int FRAME_PADDING_DP = 32;
    private static final int MARKER_DIAMETER_DP = 16;
    private static final float ROUTE_WIDTH_PX = 8f;
    /**
     * La ruta real se pinta más gruesa que la guía recta: es un trazo por calles y con este ancho
     * no se pierde entre las propias calles del mapa a poco que se aleje la cámara.
     */
    private static final float REAL_ROUTE_WIDTH_PX = 12f;

    private final Context context;
    private final int framePaddingPx;
    private final List<Marker> routeMarkers = new ArrayList<>();

    @Nullable
    private GoogleMap map;
    @Nullable
    private View mapView;
    @Nullable
    private Polyline routePolyline;
    /** Última ruta encuadrada: se reencuadra sola cuando cambia el padding (el modal creció). */
    @Nullable
    private LatLngBounds currentBounds;
    private boolean frameRetryScheduled;

    private int topPaddingPx;
    private int bottomPaddingPx;

    public MapPresenter(Context context) {
        this.context = context.getApplicationContext();
        this.framePaddingPx = Math.round(
                FRAME_PADDING_DP * context.getResources().getDisplayMetrics().density);
    }

    /** @param mapView vista del SupportMapFragment, solo para reintentar un encuadre prematuro. */
    public void attach(@NonNull GoogleMap map, @Nullable View mapView) {
        this.map = map;
        this.mapView = mapView;
        map.setPadding(0, topPaddingPx, 0, bottomPaddingPx);
    }

    public void detach() {
        map = null;
        mapView = null;
        routeMarkers.clear();
        routePolyline = null;
        currentBounds = null;
    }

    public boolean isReady() {
        return map != null;
    }

    /** Solo para el paso "elegir en el mapa": necesita leer la cámara y engancharse a su arrastre. */
    @Nullable
    public GoogleMap getMap() {
        return map;
    }

    /**
     * Zona útil del mapa: {@code topPx} es lo que tapa la tarjeta de ruta (más la status bar) y
     * {@code bottomPx} lo que tapa el modal. Reencuadra la ruta si ya hay una.
     */
    public void setContentPadding(int topPx, int bottomPx) {
        if (topPx == topPaddingPx && bottomPx == bottomPaddingPx) {
            return;
        }
        topPaddingPx = topPx;
        bottomPaddingPx = bottomPx;
        if (map == null) {
            return;
        }
        map.setPadding(0, topPx, 0, bottomPx);
        if (currentBounds != null) {
            frame(currentBounds);
        }
    }

    /**
     * Devuelve la cámara al encuadre de la ruta completa. Es la vuelta atrás del paso SEARCHING,
     * donde el mapa sí se puede arrastrar: sin esto, alejarse mirando las unidades cercanas era
     * un viaje de ida — no queda ningún reencuadre automático que lo deshaga (los que hay salen
     * de dibujar la ruta o de cambiar el padding, y ninguno de los dos vuelve a pasar ahí).
     *
     * <p>No hace nada sin ruta dibujada, que es justo lo que el host garantiza al enseñar el
     * botón solo en ese paso.
     */
    public void frameRoute() {
        if (currentBounds != null) {
            frame(currentBounds);
        }
    }

    public void setGesturesEnabled(boolean enabled) {
        if (map != null) {
            map.getUiSettings().setAllGesturesEnabled(enabled);
        }
    }

    /**
     * Los puntos del viaje sin unirlos todavía: marcadores y encuadre, sin línea.
     *
     * <p>Es el estado mientras la cotización viaja, que es de donde sale el trazo. Antes se pintaba
     * aquí la guía punteada recta, pero esa línea no es el camino que se va a recorrer, y enseñarla
     * un segundo para sustituirla después por el recorrido real hacía parpadear dos rutas distintas
     * sobre el mismo viaje. El usuario ya tiene respuesta inmediata: sus dos puntos y la cámara
     * encuadrándolos.
     */
    public void showRoutePending(@NonNull List<LatLng> points) {
        drawRoute(points, null, false);
    }

    /**
     * La ruta del viaje del pasajero: origen, parada y destino.
     *
     * <p>Se pinta el recorrido real por calles a partir de {@code encodedPolyline}, el trazo que
     * el servidor ya calculó y pagó (campo {@code polyline} de la cotización y del viaje). El
     * cliente sigue sin calcular recorrido: aquí solo se desempaqueta un dibujo ajeno.
     *
     * <p><b>La guía punteada recta ya solo es el respaldo</b>: se pinta cuando el servidor no mandó
     * trazo —el contrato dice que {@code polyline} puede faltar sin que eso invalide nada— o cuando
     * lo que mandó no se pudo leer. Que falte el dibujo nunca deja al usuario sin ruta.
     *
     * @param points          origen, parada (si la hay) y destino, en orden. Marcan los puntos que
     *                        el usuario eligió y por eso siguen mandando sobre los marcadores.
     * @param encodedPolyline trazo por calles codificado, o null.
     */
    public void showRoute(@NonNull List<LatLng> points, @Nullable String encodedPolyline) {
        drawRoute(points, encodedPolyline, true);
    }

    /**
     * @param fallbackToStraightLine si no hay trazo legible, une los puntos con la guía punteada.
     *                               Falso mientras la cotización está en vuelo: ahí todavía no se
     *                               sabe si habrá trazo, y no haberlo pedido no es un fallo.
     */
    private void drawRoute(@NonNull List<LatLng> points, @Nullable String encodedPolyline,
                           boolean fallbackToStraightLine) {
        clearRoute();
        if (map == null || points.size() < 2) {
            return;
        }

        addMarker(points.get(0), R.color.drivo_success);
        for (int i = 1; i < points.size() - 1; i++) {
            // drivo_map_accent y no drivo_primary: el azul marino de marca desaparece sobre el
            // mapa en tema oscuro (ver values/colors.xml).
            addMarker(points.get(i), R.color.drivo_map_accent);
        }
        addMarker(points.get(points.size() - 1), R.color.drivo_secondary);

        List<LatLng> realRoute = PolylineDecoder.decode(encodedPolyline);
        boolean hasRealRoute = realRoute.size() >= 2;

        if (hasRealRoute) {
            routePolyline = map.addPolyline(new PolylineOptions()
                    .addAll(realRoute)
                    .color(context.getColor(R.color.drivo_success))
                    .width(REAL_ROUTE_WIDTH_PX)
                    .jointType(JointType.ROUND)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap()));
        } else if (fallbackToStraightLine) {
            // Punteada a propósito: comunica que es una aproximación y no el camino que se va a
            // recorrer. Un trazo sólido en línea recta diría algo que no es cierto.
            List<PatternItem> dashed = Arrays.asList(new Dash(20f), new Gap(12f));
            routePolyline = map.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .color(context.getColor(R.color.drivo_success))
                    .width(ROUTE_WIDTH_PX)
                    .pattern(dashed));
        }

        // El encuadre va sobre lo que de verdad se dibujó, no solo sobre origen y destino: una ruta
        // que se abre fuera de ese rectángulo —un libramiento, un río de por medio— quedaría
        // cortada por los bordes de la pantalla.
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (LatLng point : points) {
            bounds.include(point);
        }
        for (LatLng point : realRoute) {
            bounds.include(point);
        }
        currentBounds = bounds.build();
        frame(currentBounds);
    }

    private void addMarker(LatLng position, @ColorRes int colorRes) {
        if (map == null) {
            return;
        }
        Marker marker = map.addMarker(new MarkerOptions().position(position)
                .icon(MarkerIconFactory.circle(context, colorRes, MARKER_DIAMETER_DP))
                .anchor(0.5f, 0.5f));
        if (marker != null) {
            routeMarkers.add(marker);
        }
    }

    public void clearRoute() {
        for (Marker marker : routeMarkers) {
            marker.remove();
        }
        routeMarkers.clear();
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
        currentBounds = null;
        frameRetryScheduled = false;
    }

    /**
     * Siempre animado — a diferencia de {@link RouteCamera}, aquí el mapa nunca está recién
     * creado: cuando se pide el primer encuadre ya lleva rato mostrando la ubicación del usuario,
     * y ese vuelo corto de una vista a la otra es precisamente lo que sustituye al corte de
     * navegación que había antes entre pantallas.
     */
    private void frame(@NonNull LatLngBounds bounds) {
        if (map == null) {
            return;
        }
        CameraUpdate update = CameraUpdateFactory.newLatLngBounds(bounds, framePaddingPx);
        try {
            map.animateCamera(update);
            frameRetryScheduled = false;
        } catch (IllegalStateException mapNotMeasuredYet) {
            // newLatLngBounds necesita el tamaño del mapa; si aún no hubo layout se reintenta una
            // sola vez tras el siguiente pase (mismo caso que cubre RouteCamera).
            if (frameRetryScheduled || mapView == null) {
                return;
            }
            frameRetryScheduled = true;
            mapView.post(() -> {
                if (currentBounds != null) {
                    frame(currentBounds);
                }
            });
        }
    }
}
