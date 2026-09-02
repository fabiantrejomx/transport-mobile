package com.bng.drivo.ui.trip;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.FirestoreRideRealtimeRepository;
import com.bng.drivo.data.repository.RealtimeSubscription;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.RideRealtimeRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.ui.map.MarkerIconFactory;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Viaje del pasajero desde que se asigna un conductor hasta que termina. El estado real
 * (MATCHED → DRIVER_ARRIVED → IN_PROGRESS → COMPLETED/CANCELLED_BY_DRIVER) viene del listener
 * de rides/{id}; la posición del conductor, de trips/{id}/live/driver (un solo doc
 * sobreescrito cada ~5s — se anima el salto entre lecturas, no se traza ruta real). El
 * pasajero nunca cierra el viaje: solo el conductor lo hace vía POST /driver/rides/{id}/complete.
 *
 * <p>Qué se ofrece cambia con la fase, no solo qué se dice:
 *
 * <ul>
 *   <li><b>MATCHED / DRIVER_ARRIVED</b>: llamar y enviar mensaje al conductor, y cancelar. Es
 *       cuando hace falta coordinarse ("estoy en la puerta de atrás") y todavía es válido echarse
 *       para atrás.</li>
 *   <li><b>IN_PROGRESS</b>: ya van juntos en el coche, así que llamar y escribir sobran; queda
 *       compartir el viaje, el S.O.S. y el precio acordado. Cancelar desaparece porque el
 *       contrato ya no lo permite.</li>
 * </ul>
 *
 * <p>Durante el viaje el pasajero ve además la <b>ruta</b> (origen → parada → destino) con el
 * coche encima, no solo el coche: poder comparar por dónde va con por dónde debería ir es parte
 * de la seguridad de ir en el vehículo de un desconocido. Como en el resto de la app la línea es
 * una guía punteada recta, no un trazado de calles — el cliente no calcula recorrido.
 */
public class ActiveTripActivity extends AuthenticatedActivity implements OnMapReadyCallback {

    public static final String EXTRA_RIDE_ID = "extra_ride_id";
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
    /** Parada intermedia opcional: (0,0) significa "sin parada" (el flujo admite una sola). */
    public static final String EXTRA_STOP_LAT = "extra_stop_lat";
    public static final String EXTRA_STOP_LNG = "extra_stop_lng";

    private static final LatLng DEFAULT_POSITION = new LatLng(19.4326, -99.1332);
    private static final long DRIVER_MARKER_ANIMATION_MS = 1200L;

    private final RideRealtimeRepository realtimeRepository = new FirestoreRideRealtimeRepository();
    private TripRepository tripRepository;
    private FusedLocationProviderClient fusedLocationClient;
    private RealtimeSubscription statusSubscription;
    private RealtimeSubscription locationSubscription;

    private GoogleMap googleMap;
    private String currentStatus;
    private boolean terminalStateHandled;

    private String rideId;
    private String driverInitials;
    private String driverName;
    private float price;
    private LatLng originLatLng;
    private LatLng destinationLatLng;
    @Nullable
    private LatLng stopLatLng;

    private Marker driverMarker;
    private LatLng driverPosition;
    private ValueAnimator driverAnimator;
    private Polyline routePolyline;

    private View groupBeforeTrip;
    private View groupTripInProgress;
    private TextView textStatusTitle;
    private TextView textStatusSubtitle;
    private View btnCall;
    private View btnMessage;
    private View btnShare;
    private MaterialButton btnCancelTrip;
    private View btnSosBadge;
    private TextView textSosBadgeLabel;
    private PickupWaitTimer waitTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_trip);

        tripRepository = new RestTripRepository(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        driverInitials = getIntent().getStringExtra(EXTRA_DRIVER_INITIALS);
        driverName = getIntent().getStringExtra(EXTRA_DRIVER_NAME);
        String driverDetails = getIntent().getStringExtra(EXTRA_DRIVER_DETAILS);
        price = getIntent().getFloatExtra(EXTRA_PRICE, 0f);
        originLatLng = readLatLng(EXTRA_ORIGIN_LAT, EXTRA_ORIGIN_LNG);
        destinationLatLng = readLatLng(EXTRA_DESTINATION_LAT, EXTRA_DESTINATION_LNG);
        stopLatLng = readOptionalLatLng(EXTRA_STOP_LAT, EXTRA_STOP_LNG);

        if (rideId == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.text_driver_avatar)).setText(driverInitials);
        ((TextView) findViewById(R.id.text_driver_name)).setText(driverName);
        ((TextView) findViewById(R.id.text_driver_details)).setText(driverDetails);
        ((TextView) findViewById(R.id.text_trip_price)).setText(String.format(Locale.getDefault(), "$%.2f", price));
        ((TextView) findViewById(R.id.text_trip_destination))
                .setText(getIntent().getStringExtra(EXTRA_DESTINATION));

        groupBeforeTrip = findViewById(R.id.group_before_trip);
        groupTripInProgress = findViewById(R.id.group_trip_in_progress);
        textStatusTitle = findViewById(R.id.text_trip_status_title);
        textStatusSubtitle = findViewById(R.id.text_trip_status_subtitle);
        btnCall = findViewById(R.id.btn_call_driver);
        btnMessage = findViewById(R.id.btn_message_driver);
        btnShare = findViewById(R.id.btn_share_trip);
        btnCancelTrip = findViewById(R.id.btn_cancel_trip);
        btnSosBadge = findViewById(R.id.btn_sos_badge);
        textSosBadgeLabel = findViewById(R.id.text_sos_badge_label);
        waitTimer = new PickupWaitTimer(findViewById(R.id.group_pickup_wait),
                R.string.active_trip_wait_label, R.string.active_trip_wait_hint,
                R.string.active_trip_wait_expired);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        btnCancelTrip.setOnClickListener(v -> confirmCancelTrip());
        btnSosBadge.setOnClickListener(v -> sendSos());
        btnCall.setOnClickListener(v ->
                Toast.makeText(this, R.string.active_trip_call_coming_soon, Toast.LENGTH_SHORT).show());
        btnMessage.setOnClickListener(v ->
                Toast.makeText(this, R.string.active_trip_message_coming_soon, Toast.LENGTH_SHORT).show());
        btnShare.setOnClickListener(v ->
                Toast.makeText(this, R.string.active_trip_share_coming_soon, Toast.LENGTH_SHORT).show());
    }

    private LatLng readLatLng(String latExtra, String lngExtra) {
        double lat = getIntent().getDoubleExtra(latExtra, 0);
        double lng = getIntent().getDoubleExtra(lngExtra, 0);
        if (lat == 0 && lng == 0) {
            return DEFAULT_POSITION;
        }
        return new LatLng(lat, lng);
    }

    /** Devuelve null cuando el viaje no lleva parada: (0,0) es el valor por defecto del extra. */
    @Nullable
    private LatLng readOptionalLatLng(String latExtra, String lngExtra) {
        double lat = getIntent().getDoubleExtra(latExtra, 0);
        double lng = getIntent().getDoubleExtra(lngExtra, 0);
        return lat == 0 && lng == 0 ? null : new LatLng(lat, lng);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (rideId != null) {
            statusSubscription = realtimeRepository.observeRideStatus(rideId, this::onStatusChanged);
            locationSubscription = realtimeRepository.observeDriverLocation(rideId, this::onDriverLocationChanged);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (statusSubscription != null) {
            statusSubscription.stop();
            statusSubscription = null;
        }
        if (locationSubscription != null) {
            locationSubscription.stop();
            locationSubscription = null;
        }
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

        // El viaje pudo arrancar antes de que el mapa estuviera listo (el estado llega por
        // Firestore, que no espera a nadie): entonces la ruta se dibuja aquí.
        if ("IN_PROGRESS".equals(currentStatus)) {
            drawTripRoute();
        }
    }

    private void onStatusChanged(String status) {
        if (terminalStateHandled || status.equals(currentStatus)) {
            return;
        }
        currentStatus = status;

        switch (status) {
            case "MATCHED":
                showBeforeTripUi(R.string.active_trip_status_en_route_title,
                        R.string.active_trip_status_en_route_subtitle, false);
                break;
            case "DRIVER_ARRIVED":
                showBeforeTripUi(R.string.active_trip_status_waiting_title,
                        R.string.active_trip_status_waiting_subtitle, true);
                break;
            case "IN_PROGRESS":
                showTripInProgressUi();
                break;
            case "COMPLETED":
                terminalStateHandled = true;
                goToFinishedTrip();
                break;
            case "CANCELLED_BY_DRIVER":
                terminalStateHandled = true;
                Toast.makeText(this, R.string.active_trip_cancelled_by_driver_toast, Toast.LENGTH_LONG).show();
                finish();
                break;
            default:
                // CANCELLED_BY_PASSENGER/EXPIRED_NO_DRIVERS: ya salimos localmente al cancelar,
                // o nunca deberíamos llegar aquí sin haber pasado por MATCHED primero.
                break;
        }
    }

    /**
     * Fases previas al viaje. Llamar y escribir viven aquí y solo aquí: es cuando hay algo que
     * coordinar con alguien que todavía no llega.
     *
     * @param arrived el conductor ya está en el punto — arranca la cuenta de cortesía.
     */
    private void showBeforeTripUi(int titleRes, int subtitleRes, boolean arrived) {
        groupBeforeTrip.setVisibility(View.VISIBLE);
        groupTripInProgress.setVisibility(View.GONE);
        textStatusTitle.setText(titleRes);
        textStatusSubtitle.setText(subtitleRes);

        btnCall.setVisibility(View.VISIBLE);
        btnMessage.setVisibility(View.VISIBLE);
        btnShare.setVisibility(View.GONE);

        // El contrato solo permite cancelar antes de IN_PROGRESS: botón visible y tocable,
        // S.O.S. todavía no (ver showTripInProgressUi()).
        btnCancelTrip.setVisibility(View.VISIBLE);
        btnCancelTrip.setEnabled(true);
        btnSosBadge.setVisibility(View.GONE);

        if (arrived) {
            startWaitCountdown();
        } else {
            waitTimer.stop();
        }
    }

    /**
     * La hora de llegada la pone el servidor al validar por GPS que el conductor está ahí, así
     * que se pide en vez de contar desde este teléfono: es la única forma de que los dos lados
     * vean el mismo número y de que reabrir la pantalla no reinicie la espera. Si la llamada
     * falla, {@link PickupWaitTimer} se ancla en este momento — aproximado, pero mejor que dejar
     * al pasajero sin saber cuánto le queda.
     */
    private void startWaitCountdown() {
        waitTimer.start(null);
        tripRepository.getRideDetail(rideId, new ApiCallback<com.bng.drivo.data.model.Ride>() {
            @Override
            public void onSuccess(com.bng.drivo.data.model.Ride ride) {
                if (!"DRIVER_ARRIVED".equals(currentStatus)) {
                    return;
                }
                waitTimer.start(ride.getDriverArrivedAt());
            }

            @Override
            public void onError(ApiException error) {
                // Se queda con el ancla local que ya arrancó arriba.
            }
        });
    }

    private void showTripInProgressUi() {
        groupBeforeTrip.setVisibility(View.GONE);
        groupTripInProgress.setVisibility(View.VISIBLE);
        waitTimer.stop();

        // Ya van juntos en el coche: llamar y escribir dejan de tener sentido y solo queda
        // compartir el viaje con alguien de fuera.
        btnCall.setVisibility(View.GONE);
        btnMessage.setVisibility(View.GONE);
        btnShare.setVisibility(View.VISIBLE);

        // Ya no se puede cancelar una vez IN_PROGRESS — se deshabilita, no solo se oculta, y
        // el S.O.S. toma su lugar visual arriba-izquierda.
        btnCancelTrip.setVisibility(View.GONE);
        btnCancelTrip.setEnabled(false);
        btnSosBadge.setVisibility(View.VISIBLE);

        drawTripRoute();
    }

    /**
     * La ruta contratada, con el coche encima: el pasajero puede contrastar por dónde va con por
     * dónde debería ir. No se dibuja antes de IN_PROGRESS porque hasta ese momento lo que importa
     * es dónde viene el conductor, no a dónde va el viaje.
     */
    private void drawTripRoute() {
        if (googleMap == null || routePolyline != null) {
            return;
        }
        List<LatLng> points = new ArrayList<>();
        points.add(originLatLng);
        if (stopLatLng != null) {
            points.add(stopLatLng);
        }
        points.add(destinationLatLng);

        // El origen ya lo pintó onMapReady; aquí se añaden la parada y el destino.
        if (stopLatLng != null) {
            addRouteMarker(stopLatLng, R.color.drivo_map_accent);
        }
        addRouteMarker(destinationLatLng, R.color.drivo_secondary);

        List<PatternItem> dashed = Arrays.asList(new Dash(20f), new Gap(12f));
        routePolyline = googleMap.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(8f)
                .color(getColor(R.color.drivo_success))
                .pattern(dashed));

        frameForCurrentPhase();
    }

    private void addRouteMarker(LatLng position, int colorRes) {
        googleMap.addMarker(new MarkerOptions()
                .position(position)
                .icon(MarkerIconFactory.circle(this, colorRes, 16))
                .anchor(0.5f, 0.5f));
    }

    private void onDriverLocationChanged(double lat, double lng) {
        if (googleMap == null) {
            return;
        }
        LatLng newPosition = new LatLng(lat, lng);

        if (driverMarker == null) {
            driverMarker = googleMap.addMarker(new MarkerOptions()
                    .position(newPosition)
                    .icon(MarkerIconFactory.carMarker(this, R.color.drivo_vehicle_body))
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .title(driverName));
            driverPosition = newPosition;
            frameForCurrentPhase();
            return;
        }

        animateDriverMarkerTo(newPosition);
    }

    /** El servidor solo manda la posición actual, no una ruta — el cliente interpola entre
     * la última lectura y la nueva para que el auto se vea fluido, no que salte. */
    private void animateDriverMarkerTo(LatLng newPosition) {
        LatLng start = driverPosition;
        if (start.equals(newPosition)) {
            return;
        }
        driverMarker.setRotation((float) bearingBetween(start, newPosition));

        if (driverAnimator != null) {
            driverAnimator.cancel();
        }
        driverAnimator = ValueAnimator.ofFloat(0f, 1f);
        driverAnimator.setDuration(DRIVER_MARKER_ANIMATION_MS);
        driverAnimator.setInterpolator(new LinearInterpolator());
        driverAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            double lat = start.latitude + (newPosition.latitude - start.latitude) * fraction;
            double lng = start.longitude + (newPosition.longitude - start.longitude) * fraction;
            driverMarker.setPosition(new LatLng(lat, lng));
        });
        driverAnimator.start();
        driverPosition = newPosition;
    }

    private double bearingBetween(LatLng a, LatLng b) {
        double lat1 = Math.toRadians(a.latitude);
        double lat2 = Math.toRadians(b.latitude);
        double dLng = Math.toRadians(b.longitude - a.longitude);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    /**
     * Primer encuadre con el coche ya en pantalla. Qué tiene que caber depende de la fase: antes
     * del viaje, dónde viene el conductor respecto al punto de encuentro; durante el viaje, la
     * ruta entera con el coche encima — si no, el primer aviso de posición volvería a encerrar la
     * cámara en origen+coche y se perdería de vista la ruta recién dibujada.
     */
    private void frameForCurrentPhase() {
        LatLngBounds.Builder bounds = new LatLngBounds.Builder().include(originLatLng);
        if (driverPosition != null) {
            bounds.include(driverPosition);
        }
        if ("IN_PROGRESS".equals(currentStatus)) {
            if (stopLatLng != null) {
                bounds.include(stopLatLng);
            }
            bounds.include(destinationLatLng);
        }
        frameBounds(bounds.build());
    }

    private void frameBounds(LatLngBounds bounds) {
        try {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140));
        } catch (IllegalStateException ignored) {
            // El mapa aún no tiene tamaño medido; la cámara ya quedó centrada por onMapReady.
        }
    }

    private void goToFinishedTrip() {
        Intent finishedIntent = new Intent(this, FinishedTripActivity.class);
        finishedIntent.putExtra(EXTRA_RIDE_ID, rideId);
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
                .setPositiveButton(R.string.active_trip_cancel_positive, (dialog, which) -> cancelTrip())
                .setNegativeButton(R.string.active_trip_cancel_negative, null)
                .show();
    }

    private void cancelTrip() {
        LoadingButtonHelper.setLoading(btnCancelTrip, true);
        tripRepository.cancelRide(rideId, new ApiCallback<com.bng.drivo.data.model.Ride>() {
            @Override
            public void onSuccess(com.bng.drivo.data.model.Ride result) {
                terminalStateHandled = true;
                Toast.makeText(ActiveTripActivity.this, R.string.active_trip_cancelled_toast, Toast.LENGTH_SHORT)
                        .show();
                finish();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnCancelTrip, false);
                Toast.makeText(ActiveTripActivity.this, R.string.active_trip_cancel_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void sendSos() {
        LoadingButtonHelper.setLoading(textSosBadgeLabel, true);
        if (hasLocationPermission()) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                LatLng at = location != null ? new LatLng(location.getLatitude(), location.getLongitude())
                        : (driverPosition != null ? driverPosition : originLatLng);
                dispatchSos(at);
            }).addOnFailureListener(e -> dispatchSos(driverPosition != null ? driverPosition : originLatLng));
        } else {
            dispatchSos(driverPosition != null ? driverPosition : originLatLng);
        }
    }

    private void dispatchSos(LatLng at) {
        tripRepository.sendSos(rideId, at.latitude, at.longitude, new ApiCallback<String>() {
            @Override
            public void onSuccess(String trackingUrl) {
                LoadingButtonHelper.setLoading(textSosBadgeLabel, false);
                Toast.makeText(ActiveTripActivity.this, R.string.active_trip_sos_sent, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(textSosBadgeLabel, false);
                Toast.makeText(ActiveTripActivity.this, R.string.active_trip_sos_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (driverAnimator != null) {
            driverAnimator.cancel();
        }
        if (waitTimer != null) {
            waitTimer.stop();
        }
    }
}
