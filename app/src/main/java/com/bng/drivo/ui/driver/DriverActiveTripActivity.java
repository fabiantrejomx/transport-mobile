package com.bng.drivo.ui.driver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.bng.drivo.R;
import com.bng.drivo.data.model.IncomingRequest;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.FirestoreRideRealtimeRepository;
import com.bng.drivo.data.repository.RealtimeSubscription;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.RideRealtimeRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.ui.map.MarkerIconFactory;
import com.bng.drivo.util.ColorUtils;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * C4/C7 del flujo de conductor: viaje activo desde MATCHED hasta el cobro y calificación tras
 * COMPLETED. Se abre solo por el push {@code offer_accepted} (ver DrivoFirebaseMessagingService)
 * — el contrato no tiene un "GET mi viaje activo", así que el detalle inicial (pasajero,
 * puntos, tarifa) se obtiene reutilizando GET /driver/rides/{id}, y el estado real avanza por
 * el mismo canal rides/{id} de Firestore que ya usa ActiveTripActivity del pasajero.
 *
 * Los 3 pasos del contrato (arrived/start/complete) son intencionalmente 3 taps distintos,
 * aunque el mockup C4 solo tenga un botón "Finalizar Viaje" — CLAUDE.md exige "Llegué al
 * punto" separado de iniciar/finalizar, y son 3 endpoints reales.
 *
 * Fuera de alcance por ahora (igual que el resto de la Fase 7): no hay botón de cancelar
 * viaje aquí (el mockup no lo pide) y salir de la pantalla con "atrás" no tiene forma de
 * retomarla — el contrato tampoco ofrece cómo, más allá de otro push.
 */
public class DriverActiveTripActivity extends AuthenticatedActivity implements OnMapReadyCallback {

    public static final String EXTRA_RIDE_ID = "extra_ride_id";

    private static final long LOCATION_INTERVAL_TRIP_MS = 4500L;
    private static final int STAR_COUNT = 5;

    private DriverRepository driverRepository;
    private TripRepository tripRepository;
    private final RideRealtimeRepository realtimeRepository = new FirestoreRideRealtimeRepository();
    private FusedLocationProviderClient fusedLocationClient;
    private RealtimeSubscription statusSubscription;
    private LocationCallback locationCallback;

    private GoogleMap googleMap;
    private String rideId;
    private String currentStatus;
    private boolean terminalStateHandled;

    private String passengerName;
    private double fare;
    private String pickupText;
    private String dropoffText;
    private LatLng pickupLatLng;
    private LatLng dropoffLatLng;
    private Integer pickupDistanceM;
    private Integer pickupEtaMin;
    private Integer tripDistanceM;

    private final List<TextView> cobroStarViews = new ArrayList<>();
    private int cobroRating;

    private View cardTrip;
    private TextView textTripAvatar;
    private TextView textTripPassengerName;
    private TextView textTripPassengerRating;
    private TextView textTripActionTitle;
    private TextView textTripActionSubtitle;
    private TextView textTripFare;
    private TextView textTripSecondaryStatLabel;
    private TextView textTripSecondaryStatValue;
    private MaterialButton btnTripAction;

    private View cardCobroRating;
    private TextView textCobroAmount;
    private TextView textCobroCommissionNote;
    private TextView textCobroRatingPrompt;
    private LinearLayout containerCobroStars;
    private MaterialButton btnCobroClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_active_trip);

        rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        if (rideId == null) {
            finish();
            return;
        }

        driverRepository = new RestDriverRepository(this);
        tripRepository = new RestTripRepository(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        cardTrip = findViewById(R.id.card_trip);
        textTripAvatar = findViewById(R.id.text_trip_avatar);
        textTripPassengerName = findViewById(R.id.text_trip_passenger_name);
        textTripPassengerRating = findViewById(R.id.text_trip_passenger_rating);
        textTripActionTitle = findViewById(R.id.text_trip_action_title);
        textTripActionSubtitle = findViewById(R.id.text_trip_action_subtitle);
        textTripFare = findViewById(R.id.text_trip_fare);
        textTripSecondaryStatLabel = findViewById(R.id.text_trip_secondary_stat_label);
        textTripSecondaryStatValue = findViewById(R.id.text_trip_secondary_stat_value);
        btnTripAction = findViewById(R.id.btn_trip_action);

        cardCobroRating = findViewById(R.id.card_cobro_rating);
        textCobroAmount = findViewById(R.id.text_cobro_amount);
        textCobroCommissionNote = findViewById(R.id.text_cobro_commission_note);
        textCobroRatingPrompt = findViewById(R.id.text_cobro_rating_prompt);
        containerCobroStars = findViewById(R.id.container_cobro_stars);
        btnCobroClose = findViewById(R.id.btn_cobro_close);

        findViewById(R.id.btn_trip_sos).setOnClickListener(v -> sendSos());
        findViewById(R.id.btn_trip_waze).setOnClickListener(v -> openWaze());
        btnCobroClose.setOnClickListener(v -> submitRating());
        setUpCobroStars();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fetchRequestDetails();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(this, googleMap);
        drawTripMap();
    }

    private void fetchRequestDetails() {
        driverRepository.getIncomingRequest(rideId, new ApiCallback<IncomingRequest>() {
            @Override
            public void onSuccess(IncomingRequest request) {
                passengerName = request.getPassengerName();
                fare = request.getOffer();
                pickupText = request.getPickupText();
                dropoffText = request.getDropoffText();
                pickupDistanceM = request.getPickupDistanceM();
                pickupEtaMin = request.getPickupEtaMin();
                tripDistanceM = request.getTripDistanceM();
                pickupLatLng = request.getPickupLat() != null && request.getPickupLng() != null
                        ? new LatLng(request.getPickupLat(), request.getPickupLng()) : null;
                dropoffLatLng = request.getDropoffLat() != null && request.getDropoffLng() != null
                        ? new LatLng(request.getDropoffLat(), request.getDropoffLng()) : null;

                textTripAvatar.setText(initialsFor(passengerName));
                textTripPassengerName.setText(passengerName);
                textTripPassengerRating.setText(request.getPassengerRating() != null
                        ? String.format(Locale.getDefault(), "★ %.1f", request.getPassengerRating()) : "");
                textTripFare.setText(String.format(Locale.getDefault(), "$%.2f", fare));

                drawTripMap();
            }

            @Override
            public void onError(ApiException error) {
                Toast.makeText(DriverActiveTripActivity.this, R.string.driver_trip_load_error, Toast.LENGTH_LONG)
                        .show();
                finish();
            }
        });
    }

    private void drawTripMap() {
        if (googleMap == null || pickupLatLng == null) {
            return;
        }
        googleMap.clear();
        if (hasLocationPermission()) {
            googleMap.setMyLocationEnabled(true);
        }

        List<LatLng> points = new ArrayList<>();
        points.add(pickupLatLng);
        googleMap.addMarker(new MarkerOptions().position(pickupLatLng)
                .icon(MarkerIconFactory.circle(this, R.color.drivo_success, 16))
                .anchor(0.5f, 0.5f));

        if (dropoffLatLng != null) {
            points.add(dropoffLatLng);
            googleMap.addMarker(new MarkerOptions().position(dropoffLatLng)
                    .icon(MarkerIconFactory.circle(this, R.color.drivo_secondary, 16))
                    .anchor(0.5f, 0.5f));

            List<PatternItem> dashed = Arrays.asList(new Dash(20f), new Gap(12f));
            googleMap.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .width(8f)
                    .color(getColor(R.color.drivo_success))
                    .pattern(dashed));
        }

        try {
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (LatLng point : points) {
                bounds.include(point);
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 160));
        } catch (IllegalStateException ignored) {
            // El mapa aún no tiene tamaño medido; se reintenta con el próximo layout.
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        statusSubscription = realtimeRepository.observeRideStatus(rideId, this::onStatusChanged);
        startLocationLoop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (statusSubscription != null) {
            statusSubscription.stop();
            statusSubscription = null;
        }
        stopLocationLoop();
    }

    private void onStatusChanged(String status) {
        if (terminalStateHandled || status.equals(currentStatus)) {
            return;
        }
        currentStatus = status;

        switch (status) {
            case "MATCHED":
                showPickupPhase(false);
                break;
            case "DRIVER_ARRIVED":
                showPickupPhase(true);
                break;
            case "IN_PROGRESS":
                showInProgressPhase();
                break;
            case "COMPLETED":
                // Llegamos aquí sin pasar por attemptComplete() (p. ej. la app se cerró justo
                // al tocar "Finalizar" y se reabrió después) — sin la respuesta de
                // POST /driver/rides/{id}/complete no sabemos la comisión real, así que no
                // fabricamos la pantalla de cobro con un número inventado.
                terminalStateHandled = true;
                stopLocationLoop();
                finish();
                break;
            case "CANCELLED_BY_PASSENGER":
                terminalStateHandled = true;
                stopLocationLoop();
                Toast.makeText(this, R.string.driver_trip_cancelled_by_passenger_toast, Toast.LENGTH_LONG).show();
                finish();
                break;
            default:
                break;
        }
    }

    private void showPickupPhase(boolean arrived) {
        cardTrip.setVisibility(View.VISIBLE);
        cardCobroRating.setVisibility(View.GONE);

        textTripActionTitle.setText(getString(R.string.driver_trip_pickup_title_format, passengerName));
        double pickupKm = pickupDistanceM != null ? pickupDistanceM / 1000.0 : 0;
        textTripActionSubtitle.setText(
                getString(R.string.driver_trip_pickup_subtitle_format, pickupText, pickupKm));

        textTripSecondaryStatLabel.setText(R.string.driver_trip_eta_stat_label);
        textTripSecondaryStatValue.setText(pickupEtaMin != null
                ? getString(R.string.searching_eta_min, pickupEtaMin) : "--");

        btnTripAction.setText(arrived ? R.string.driver_trip_action_start : R.string.driver_trip_action_arrived);
        btnTripAction.setEnabled(true);
        btnTripAction.setOnClickListener(v -> {
            if (arrived) {
                attemptStart();
            } else {
                attemptArrived();
            }
        });
    }

    private void showInProgressPhase() {
        cardTrip.setVisibility(View.VISIBLE);
        cardCobroRating.setVisibility(View.GONE);

        textTripActionTitle.setText(getString(R.string.driver_trip_dropoff_title_format, passengerName));
        long tripKm = tripDistanceM != null ? Math.round(tripDistanceM / 1000.0) : 0;
        textTripActionSubtitle.setText(
                getString(R.string.driver_trip_dropoff_subtitle_format, dropoffText, tripKm));

        textTripSecondaryStatLabel.setText(R.string.driver_trip_progress_stat_label);
        textTripSecondaryStatValue.setText(R.string.driver_trip_progress_stat_value);

        btnTripAction.setText(R.string.driver_trip_action_complete);
        btnTripAction.setEnabled(true);
        btnTripAction.setOnClickListener(v -> attemptComplete());

        drawTripMap();
    }

    private void showCobroRatingPhase(Ride completedRide) {
        cardTrip.setVisibility(View.GONE);
        cardCobroRating.setVisibility(View.VISIBLE);

        double agreedFare = completedRide.getAgreedFare() != null ? completedRide.getAgreedFare() : fare;
        textCobroAmount.setText(String.format(Locale.getDefault(), "$%.0f", agreedFare));

        Double commission = completedRide.getCommission();
        textCobroCommissionNote.setText(commission != null
                ? getString(R.string.driver_cobro_commission_note_format, commission) : "");
        textCobroRatingPrompt.setText(getString(R.string.driver_cobro_rating_prompt_format, passengerName));
    }

    @SuppressLint("MissingPermission")
    private void attemptArrived() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, R.string.driver_home_location_permission_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        LoadingButtonHelper.setLoading(btnTripAction, true);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        LoadingButtonHelper.setLoading(btnTripAction, false);
                        Toast.makeText(this, R.string.driver_trip_arrived_error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    driverRepository.markArrived(rideId, location.getLatitude(), location.getLongitude(),
                            new ApiCallback<Ride>() {
                                @Override
                                public void onSuccess(Ride result) {
                                    LoadingButtonHelper.setLoading(btnTripAction, false);
                                }

                                @Override
                                public void onError(ApiException error) {
                                    LoadingButtonHelper.setLoading(btnTripAction, false);
                                    Toast.makeText(DriverActiveTripActivity.this,
                                            R.string.driver_trip_arrived_error, Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    LoadingButtonHelper.setLoading(btnTripAction, false);
                    Toast.makeText(this, R.string.driver_trip_arrived_error, Toast.LENGTH_SHORT).show();
                });
    }

    private void attemptStart() {
        LoadingButtonHelper.setLoading(btnTripAction, true);
        driverRepository.startRide(rideId, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride result) {
                LoadingButtonHelper.setLoading(btnTripAction, false);
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnTripAction, false);
                Toast.makeText(DriverActiveTripActivity.this, R.string.driver_trip_start_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void attemptComplete() {
        LoadingButtonHelper.setLoading(btnTripAction, true);
        driverRepository.completeRide(rideId, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride result) {
                LoadingButtonHelper.setLoading(btnTripAction, false);
                terminalStateHandled = true;
                currentStatus = "COMPLETED";
                stopLocationLoop();
                showCobroRatingPhase(result);
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnTripAction, false);
                Toast.makeText(DriverActiveTripActivity.this, R.string.driver_trip_complete_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void submitRating() {
        if (cobroRating == 0) {
            Toast.makeText(this, R.string.driver_cobro_rating_required_error, Toast.LENGTH_SHORT).show();
            return;
        }
        LoadingButtonHelper.setLoading(btnCobroClose, true);
        driverRepository.rateRide(rideId, cobroRating, null, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                finish();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnCobroClose, false);
                Toast.makeText(DriverActiveTripActivity.this, R.string.driver_cobro_rating_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void setUpCobroStars() {
        int sizePx = (int) (8 * getResources().getDisplayMetrics().density);
        for (int i = 1; i <= STAR_COUNT; i++) {
            TextView star = new TextView(this);
            star.setText("★");
            star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f);
            star.setPadding(sizePx, 0, sizePx, 0);
            int starIndex = i;
            star.setOnClickListener(v -> {
                cobroRating = starIndex;
                updateCobroStars();
            });
            containerCobroStars.addView(star);
            cobroStarViews.add(star);
        }
        updateCobroStars();
    }

    private void updateCobroStars() {
        int selectedColor = ColorUtils.resolveThemeColor(this, com.google.android.material.R.attr.colorSecondary);
        int unselectedColor = ColorUtils.resolveThemeColor(this, com.google.android.material.R.attr.colorOutline);
        for (int i = 0; i < cobroStarViews.size(); i++) {
            cobroStarViews.get(i).setTextColor(i < cobroRating ? selectedColor : unselectedColor);
        }
    }

    private void openWaze() {
        LatLng target = "IN_PROGRESS".equals(currentStatus) ? dropoffLatLng : pickupLatLng;
        if (target == null) {
            return;
        }
        Uri uri = Uri.parse("https://waze.com/ul?ll=" + target.latitude + "," + target.longitude + "&navigate=yes");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.driver_trip_waze_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void sendSos() {
        LatLng fallback = "IN_PROGRESS".equals(currentStatus) && dropoffLatLng != null ? dropoffLatLng
                : pickupLatLng;
        if (hasLocationPermission()) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> dispatchSos(location != null
                            ? new LatLng(location.getLatitude(), location.getLongitude()) : fallback))
                    .addOnFailureListener(e -> dispatchSos(fallback));
        } else {
            dispatchSos(fallback);
        }
    }

    private void dispatchSos(LatLng at) {
        if (at == null) {
            return;
        }
        tripRepository.sendSos(rideId, at.latitude, at.longitude, new ApiCallback<String>() {
            @Override
            public void onSuccess(String trackingUrl) {
                Toast.makeText(DriverActiveTripActivity.this, R.string.active_trip_sos_sent, Toast.LENGTH_LONG)
                        .show();
            }

            @Override
            public void onError(ApiException error) {
                Toast.makeText(DriverActiveTripActivity.this, R.string.active_trip_sos_error, Toast.LENGTH_LONG)
                        .show();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void startLocationLoop() {
        LocationRequest request = new LocationRequest.Builder(LOCATION_INTERVAL_TRIP_MS)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(LOCATION_INTERVAL_TRIP_MS)
                .build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                android.location.Location location = result.getLastLocation();
                if (location == null) {
                    return;
                }
                Double heading = location.hasBearing() ? (double) location.getBearing() : null;
                Double accuracy = location.hasAccuracy() ? (double) location.getAccuracy() : null;
                driverRepository.reportLocation(location.getLatitude(), location.getLongitude(), heading, accuracy,
                        new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                            }

                            @Override
                            public void onError(ApiException error) {
                            }
                        });
            }
        };
        if (hasLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        }
    }

    private void stopLocationLoop() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String initialsFor(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length && initials.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return initials.toString();
    }
}
