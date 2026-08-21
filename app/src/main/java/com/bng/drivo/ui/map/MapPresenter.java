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

    public void setGesturesEnabled(boolean enabled) {
        if (map != null) {
            map.getUiSettings().setAllGesturesEnabled(enabled);
        }
    }

    /**
     * Guía punteada recta entre origen, parada y destino — no es una ruta real de calles: el
     * contrato es explícito en que el cliente no calcula recorrido (ver openapi.yaml).
     */
    public void showRoute(@NonNull List<LatLng> points) {
        clearRoute();
        if (map == null || points.size() < 2) {
            return;
        }

        addMarker(points.get(0), R.color.drivo_success);
        for (int i = 1; i < points.size() - 1; i++) {
            addMarker(points.get(i), R.color.drivo_primary);
        }
        addMarker(points.get(points.size() - 1), R.color.drivo_secondary);

        List<PatternItem> dashed = Arrays.asList(new Dash(20f), new Gap(12f));
        routePolyline = map.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(ROUTE_WIDTH_PX)
                .color(context.getColor(R.color.drivo_success))
                .pattern(dashed));

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (LatLng point : points) {
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
