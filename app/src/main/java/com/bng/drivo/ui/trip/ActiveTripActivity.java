package com.bng.drivo.ui.trip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

import com.bng.drivo.R;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.ui.map.MarkerIconFactory;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Viaje del pasajero desde que elige conductor hasta que finaliza, en 3 fases emuladas
 * (no hay backend/Firebase para el conductor todavía, ver CLAUDE.md):
 *
 * 1) DRIVER_EN_ROUTE — el conductor "viene en camino": se anima su marcador desde un punto
 *    cercano simulado hasta el punto de recogida del pasajero, con la ruta dibujada. Solo
 *    permite cancelar, sin S.O.S. (el viaje todavía no arrancó).
 * 2) DRIVER_WAITING — el conductor "llegó" y espera; el pasajero confirma "Ya voy saliendo".
 * 3) TRIP_IN_PROGRESS — arranca el trayecto hacia el destino (nueva ruta animada), con
 *    S.O.S., compartir viaje y cancelar con confirmación, más finalizar viaje manual.
 */
public class ActiveTripActivity extends AuthenticatedActivity implements OnMapReadyCallback {

    public static final String EXTRA_DRIVER_INITIALS = "extra_driver_initials";
    public static final String EXTRA_DRIVER_NAME = "extra_driver_name";
    public static final String EXTRA_DRIVER_DETAILS = "extra_driver_details";
    public static final String EXTRA_PRICE = "extra_price";
    public static final String EXTRA_ORIGIN = "extra_origin";
    public static final String EXTRA_DESTINATION = "extra_destination";
    public static final String EXTRA_ORIGIN_LAT = "extra_origin_lat";
    public static final String EXTRA_ORIGIN_LNG = "extra_origin_lng";
    public static final String EXTRA_DESTINATION_LAT = "extra_destination_lat";
    public static final String EXTRA_DESTINATION_LNG = "extra_destination_lng";

    private static final LatLng DEFAULT_POSITION = new LatLng(19.4326, -99.1332);

    private static final long ROUTE_TO_PICKUP_DURATION_MS = 22_000L;
    private static final long MIN_TRIP_DURATION_MS = 30_000L;
    private static final long MAX_TRIP_DURATION_MS = 90_000L;
    // Valor de prueba: en producción el conductor esperaría más (p. ej. 5 min) antes de
    // arrancar el trayecto aunque el pasajero no confirme "Ya voy saliendo".
    private static final long WAITING_AUTO_START_MS = 60_000L;

    private enum TripStage { DRIVER_EN_ROUTE, DRIVER_WAITING, TRIP_IN_PROGRESS }

    private final Random random = new Random();

    private GoogleMap googleMap;
    private TripStage stage = TripStage.DRIVER_EN_ROUTE;

    private String driverInitials;
    private String driverName;
    private float price;
    private LatLng originLatLng;
    private LatLng destinationLatLng;
    private int etaMinutes;
    private String shareTripId;

    private Marker driverMarker;
    private Marker destinationMarker;
    private LatLng driverPosition;
    private Polyline routePolyline;
    private ValueAnimator driverAnimator;
    private CountDownTimer graceTimer;

    private View groupBeforeTrip;
    private View groupTripInProgress;
    private View cardGraceTimer;
    private TextView textStatusTitle;
    private TextView textStatusSubtitle;
    private View btnShare;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_trip);

        driverInitials = getIntent().getStringExtra(EXTRA_DRIVER_INITIALS);
        driverName = getIntent().getStringExtra(EXTRA_DRIVER_NAME);
        String driverDetails = getIntent().getStringExtra(EXTRA_DRIVER_DETAILS);
        price = getIntent().getFloatExtra(EXTRA_PRICE, 0f);
        originLatLng = readLatLng(EXTRA_ORIGIN_LAT, EXTRA_ORIGIN_LNG);
        destinationLatLng = readLatLng(EXTRA_DESTINATION_LAT, EXTRA_DESTINATION_LNG);
        shareTripId = UUID.randomUUID().toString().substring(0, 8);
        etaMinutes = 3 + random.nextInt(5);

        ((TextView) findViewById(R.id.text_driver_avatar)).setText(driverInitials);
        ((TextView) findViewById(R.id.text_driver_name)).setText(driverName);
        ((TextView) findViewById(R.id.text_driver_details)).setText(driverDetails);
        ((TextView) findViewById(R.id.text_trip_price)).setText(String.format(Locale.getDefault(), "$%.2f", price));

        groupBeforeTrip = findViewById(R.id.group_before_trip);
        groupTripInProgress = findViewById(R.id.group_trip_in_progress);
        cardGraceTimer = findViewById(R.id.card_grace_timer);
        textStatusTitle = findViewById(R.id.text_trip_status_title);
        textStatusSubtitle = findViewById(R.id.text_trip_status_subtitle);
        btnShare = findViewById(R.id.btn_share_trip);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        findViewById(R.id.btn_cancel_trip).setOnClickListener(v -> confirmCancelTrip());
        findViewById(R.id.btn_im_leaving).setOnClickListener(v -> confirmLeaving());
        findViewById(R.id.btn_finish_trip).setOnClickListener(v -> finishTrip());
        findViewById(R.id.btn_sos).setOnClickListener(v ->
                Toast.makeText(this, R.string.active_trip_sos_coming_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_call_driver).setOnClickListener(v ->
                Toast.makeText(this, R.string.active_trip_call_coming_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_message_driver).setOnClickListener(v ->
                Toast.makeText(this, R.string.active_trip_message_coming_soon, Toast.LENGTH_SHORT).show());
        btnShare.setOnClickListener(v -> shareTrip());
    }

    private LatLng readLatLng(String latExtra, String lngExtra) {
        double lat = getIntent().getDoubleExtra(latExtra, 0);
        double lng = getIntent().getDoubleExtra(lngExtra, 0);
        if (lat == 0 && lng == 0) {
            return DEFAULT_POSITION;
        }
        return new LatLng(lat, lng);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(this, googleMap);
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.fromLatLngZoom(originLatLng, 14f)));

        googleMap.addMarker(new MarkerOptions()
                .position(originLatLng)
                .icon(MarkerIconFactory.circle(this, R.color.drivo_success, 16))
                .anchor(0.5f, 0.5f)
                .title(getString(R.string.home_origin_placeholder)));

        googleMap.setOnMapLoadedCallback(this::startDriverEnRoute);
    }

    private void startDriverEnRoute() {
        stage = TripStage.DRIVER_EN_ROUTE;
        LatLng driverStart = SimulatedRoute.spawnNearbyDriverStart(originLatLng);
        SimulatedRoute route = SimulatedRoute.between(driverStart, originLatLng);

        driverMarker = googleMap.addMarker(new MarkerOptions()
                .position(driverStart)
                .icon(MarkerIconFactory.carMarker(this, R.color.drivo_primary))
                .anchor(0.5f, 0.5f)
                .flat(true)
                .rotation((float) SimulatedRoute.bearingBetween(driverStart, originLatLng))
                .title(driverName));

        drawRoute(route, getColor(R.color.drivo_secondary));
        moveCameraToBounds(route.getPoints());

        textStatusTitle.setText(R.string.active_trip_status_en_route_title);
        updateEtaSubtitle(etaMinutes);

        driverPosition = driverStart;
        driverAnimator = ValueAnimator.ofFloat(0f, 1f);
        driverAnimator.setDuration(ROUTE_TO_PICKUP_DURATION_MS);
        driverAnimator.setInterpolator(new LinearInterpolator());
        driverAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            moveDriverMarker(route.pointAt(fraction));
            int remainingMinutes = Math.max(1, Math.round((1 - fraction) * etaMinutes));
            updateEtaSubtitle(remainingMinutes);
        });
        driverAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startDriverWaiting();
            }
        });
        driverAnimator.start();
    }

    private void startDriverWaiting() {
        stage = TripStage.DRIVER_WAITING;
        clearRoute();
        View btnLeaving = findViewById(R.id.btn_im_leaving);
        btnLeaving.setVisibility(View.VISIBLE);
        btnLeaving.setEnabled(true);
        textStatusTitle.setText(R.string.active_trip_status_waiting_title);
        textStatusSubtitle.setText(R.string.active_trip_status_waiting_subtitle);
        startWaitingTimer();
    }

    /** El pasajero solo confirma que va saliendo; no controla cuándo arranca el trayecto
     * (eso lo decide el temporizador de espera, ver {@link #startWaitingTimer()}). */
    private void confirmLeaving() {
        findViewById(R.id.btn_im_leaving).setEnabled(false);
    }

    private void startTripInProgress() {
        if (stage == TripStage.TRIP_IN_PROGRESS) {
            return;
        }
        stage = TripStage.TRIP_IN_PROGRESS;
        groupBeforeTrip.setVisibility(View.GONE);
        groupTripInProgress.setVisibility(View.VISIBLE);
        btnShare.setVisibility(View.VISIBLE);

        if (destinationMarker == null) {
            destinationMarker = googleMap.addMarker(new MarkerOptions()
                    .position(destinationLatLng)
                    .icon(MarkerIconFactory.circle(this, R.color.drivo_secondary, 16))
                    .anchor(0.5f, 0.5f));
        }

        SimulatedRoute route = SimulatedRoute.between(originLatLng, destinationLatLng);
        drawRoute(route, getColor(R.color.drivo_success));
        moveCameraToBounds(route.getPoints());

        long durationMs = (long) Math.max(MIN_TRIP_DURATION_MS,
                Math.min(MAX_TRIP_DURATION_MS, route.getTotalDistanceMeters() * 12));

        driverPosition = originLatLng;
        driverAnimator = ValueAnimator.ofFloat(0f, 1f);
        driverAnimator.setDuration(durationMs);
        driverAnimator.setInterpolator(new LinearInterpolator());
        driverAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            moveDriverMarker(route.pointAt(fraction));
        });
        driverAnimator.start();
    }

    /** Corre durante toda la fase DRIVER_WAITING, sin importar si el pasajero ya pulsó
     * "Ya voy saliendo": al agotarse, el trayecto arranca de todas formas. */
    private void startWaitingTimer() {
        cardGraceTimer.setVisibility(View.VISIBLE);
        TextView textTimer = findViewById(R.id.text_grace_timer);
        graceTimer = new CountDownTimer(WAITING_AUTO_START_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long totalSeconds = millisUntilFinished / 1000;
                String time = String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60);
                textTimer.setText(getString(R.string.active_trip_grace_timer, time));
            }

            @Override
            public void onFinish() {
                cardGraceTimer.setVisibility(View.GONE);
                startTripInProgress();
            }
        };
        graceTimer.start();
    }

    /** Mueve el marcador del conductor y lo reorienta según el rumbo hacia la nueva posición,
     * como el indicativo de coche de Uber/Didi/InDrive girando sobre las calles. */
    private void moveDriverMarker(LatLng newPosition) {
        if (!newPosition.equals(driverPosition)) {
            driverMarker.setRotation((float) SimulatedRoute.bearingBetween(driverPosition, newPosition));
            driverPosition = newPosition;
        }
        driverMarker.setPosition(newPosition);
    }

    private void updateEtaSubtitle(int minutes) {
        textStatusSubtitle.setText(getString(R.string.active_trip_status_en_route_subtitle, minutes));
    }

    private void drawRoute(SimulatedRoute route, int color) {
        clearRoute();
        PolylineOptions options = new PolylineOptions().width(10f).color(color);
        for (LatLng point : route.getPoints()) {
            options.add(point);
        }
        routePolyline = googleMap.addPolyline(options);
    }

    private void clearRoute() {
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
    }

    private void moveCameraToBounds(List<LatLng> points) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }
        try {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 140));
        } catch (IllegalStateException ignored) {
            // El mapa aún no tiene tamaño medido; la cámara ya quedó centrada por onMapReady.
        }
    }

    private void shareTrip() {
        String link = "https://drivo.mx/viaje/" + shareTripId;
        String message = getString(R.string.active_trip_share_text, driverName, link);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.active_trip_share_chooser_title)));
    }

    private void finishTrip() {
        Intent finishedIntent = new Intent(this, FinishedTripActivity.class);
        finishedIntent.putExtra(EXTRA_DRIVER_INITIALS, driverInitials);
        finishedIntent.putExtra(EXTRA_DRIVER_NAME, driverName);
        finishedIntent.putExtra(EXTRA_PRICE, price);
        startActivity(finishedIntent);
        finish();
    }

    private void confirmCancelTrip() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.active_trip_cancel_title)
                .setMessage(R.string.active_trip_cancel_message)
                .setPositiveButton(R.string.active_trip_cancel_positive, (dialog, which) -> {
                    Toast.makeText(this, R.string.active_trip_cancelled_toast, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.active_trip_cancel_negative, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (driverAnimator != null) {
            driverAnimator.cancel();
        }
        if (graceTimer != null) {
            graceTimer.cancel();
        }
    }
}
