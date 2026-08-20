package com.bng.drivo.ui.driver;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.bng.drivo.R;
import com.bng.drivo.data.model.DriverApplication;
import com.bng.drivo.data.model.IncomingRequest;
import com.bng.drivo.data.model.Wallet;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.DriverRideRealtimeRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.FirestoreDriverRideRealtimeRepository;
import com.bng.drivo.data.repository.RealtimeSubscription;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import com.bng.drivo.ui.map.MapStyler;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * C2/C2-B del flujo de conductor: Home (desconectado) y Radar (en línea), en la misma
 * Activity — igual que el mockup solo cambia de estado visual, no de pantalla.
 *
 * Antes de mostrar nada gatea por GET /driver/application: solo un conductor "approved" ve
 * el botón de conectarse; los demás estados (draft/pending_review/rejected/suspended) muestran
 * una tarjeta de estado en su lugar — y en ninguno de esos estados se infla el mapa ni se pide
 * el permiso de ubicación (ver setUpMapAndLocationIfNeeded()), porque el conductor no tiene
 * acceso a esa parte de la app hasta que lo aprueban. Los conductores que aún no tienen
 * ninguna solicitud ni siquiera llegan aquí — DriverEntryPoint los manda directo a
 * DriverRegistrationActivity desde el login.
 *
 * C3 (oferta entrante) vive aquí mismo como una tarjeta superpuesta al radar, no como una
 * Activity separada — el contrato es explícito (openapi.yaml, POST /driver/rides/{id}/offer):
 * "el conductor no se bloquea, después de ofertar vuelve al radar y puede seguir recibiendo
 * viajes". Por eso ofertar/contraofertar cierra la tarjeta de inmediato en vez de esperar
 * una respuesta del servidor.
 */
public class DriverHomeActivity extends AuthenticatedActivity implements OnMapReadyCallback {

    private static final long RADAR_PULSE_DURATION_MS = 1600L;
    private static final long LOCATION_INTERVAL_IDLE_MS = 12000L;

    private DriverRepository driverRepository;
    private AuthRepository authRepository;
    private final DriverRideRealtimeRepository realtimeRepository = new FirestoreDriverRideRealtimeRepository();
    private FusedLocationProviderClient fusedLocationClient;

    private GoogleMap googleMap;
    private boolean mapInitialized;
    private ValueAnimator radarAnimator;
    private RealtimeSubscription inboxSubscription;
    private LocationCallback locationCallback;
    private CountDownTimer incomingExpiryTimer;
    private String displayedRideId;
    private String ignoredRideId;

    private boolean online;
    private boolean approved;

    private View topBar;
    private View btnMenu;
    private TextView textStatusBadge;
    private View radarContainer;
    private View cardNotApproved;
    private TextView textNotApprovedTitle;
    private TextView textNotApprovedDetail;
    private View progressGate;
    private View bottomActions;
    private MaterialButton btnConnectToggle;
    private TextView textConnectionStatus;

    private View cardIncomingRequest;
    private TextView textIncomingAvatar;
    private TextView textIncomingName;
    private TextView textIncomingRating;
    private TextView textIncomingOffer;
    private TextView textIncomingPickupDistance;
    private TextView textIncomingDropoffText;
    private TextView textIncomingTripDistance;
    private TextView textIncomingCounterLabel;
    private LinearLayout containerIncomingCounters;
    private MaterialButton btnIncomingIgnore;
    private MaterialButton btnIncomingAccept;
    private ProgressBar progressIncomingExpiry;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                if (hasLocationPermission() && googleMap != null) {
                    enableMyLocation();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_home);

        driverRepository = new RestDriverRepository(this);
        authRepository = new FirebaseAuthRepository();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        topBar = findViewById(R.id.top_bar);
        textStatusBadge = findViewById(R.id.text_status_badge);
        radarContainer = findViewById(R.id.radar_container);
        cardNotApproved = findViewById(R.id.card_not_approved);
        textNotApprovedTitle = findViewById(R.id.text_not_approved_title);
        textNotApprovedDetail = findViewById(R.id.text_not_approved_detail);
        progressGate = findViewById(R.id.progress_gate);
        bottomActions = findViewById(R.id.bottom_actions);
        btnConnectToggle = findViewById(R.id.btn_connect_toggle);
        textConnectionStatus = findViewById(R.id.text_connection_status);

        cardIncomingRequest = findViewById(R.id.card_incoming_request);
        textIncomingAvatar = findViewById(R.id.text_incoming_avatar);
        textIncomingName = findViewById(R.id.text_incoming_name);
        textIncomingRating = findViewById(R.id.text_incoming_rating);
        textIncomingOffer = findViewById(R.id.text_incoming_offer);
        textIncomingPickupDistance = findViewById(R.id.text_incoming_pickup_distance);
        textIncomingDropoffText = findViewById(R.id.text_incoming_dropoff_text);
        textIncomingTripDistance = findViewById(R.id.text_incoming_trip_distance);
        textIncomingCounterLabel = findViewById(R.id.text_incoming_counter_label);
        containerIncomingCounters = findViewById(R.id.container_incoming_counters);
        btnIncomingIgnore = findViewById(R.id.btn_incoming_ignore);
        btnIncomingAccept = findViewById(R.id.btn_incoming_accept);
        progressIncomingExpiry = findViewById(R.id.progress_incoming_expiry);

        btnMenu = findViewById(R.id.btn_menu);
        btnMenu.setVisibility(View.GONE);
        btnMenu.setOnClickListener(v -> DriverMenuBottomSheet.present(getSupportFragmentManager()));
        cardNotApproved.setOnClickListener(v -> runApplicationGate());
        btnConnectToggle.setOnClickListener(v -> toggleConnection());
        btnIncomingIgnore.setOnClickListener(v -> ignoreIncomingRequest());

        startRadarPulse();
        runApplicationGate();
    }

    /**
     * El mapa y el permiso de ubicación se piden aquí, no en onCreate(): un conductor sin
     * aprobar todavía no tiene acceso a esa parte de la app, así que ni el mapa se infla ni el
     * diálogo de permiso aparece hasta que el gate confirma "approved". mapInitialized evita
     * repetir la transacción si el gate se vuelve a correr (p. ej. al reintentar).
     */
    private void setUpMapAndLocationIfNeeded() {
        if (mapInitialized) {
            return;
        }
        mapInitialized = true;

        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.map_container, mapFragment).commit();
        mapFragment.getMapAsync(this);

        if (hasLocationPermission()) {
            fusedLocationClient.getLastLocation();
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(this, googleMap);
        if (hasLocationPermission()) {
            enableMyLocation();
        }
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        googleMap.setMyLocationEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(location.getLatitude(), location.getLongitude()), 16f));
            }
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void runApplicationGate() {
        progressGate.setVisibility(View.VISIBLE);
        cardNotApproved.setVisibility(View.GONE);
        bottomActions.setVisibility(View.GONE);
        btnMenu.setVisibility(View.GONE);
        driverRepository.getApplication(new ApiCallback<DriverApplication>() {
            @Override
            public void onSuccess(DriverApplication application) {
                progressGate.setVisibility(View.GONE);
                if ("approved".equals(application.getStatus())) {
                    approved = true;
                    showApprovedState();
                    loadWallet();
                } else {
                    approved = false;
                    showNotApprovedState(application);
                }
            }

            @Override
            public void onError(ApiException error) {
                progressGate.setVisibility(View.GONE);
                approved = false;
                if (error.getCode() == ApiErrorCode.NO_APPLICATION) {
                    startActivity(new Intent(DriverHomeActivity.this, DriverRegistrationActivity.class));
                    finish();
                    return;
                }
                cardNotApproved.setVisibility(View.VISIBLE);
                textNotApprovedTitle.setText(R.string.driver_home_status_title_error);
                textNotApprovedDetail.setText(R.string.driver_home_status_detail_error);
            }
        });
    }

    private void showApprovedState() {
        cardNotApproved.setVisibility(View.GONE);
        bottomActions.setVisibility(View.VISIBLE);
        btnMenu.setVisibility(View.VISIBLE);
        updateConnectionUi();
        setUpMapAndLocationIfNeeded();
    }

    /** Sin acceso a Ganancias/Configuración/Seguridad todavía — esas pantallas asumen un
     * conductor ya operando, y aquí solo hay una solicitud en algún estado no aprobado. */
    private void showNotApprovedState(DriverApplication application) {
        bottomActions.setVisibility(View.GONE);
        btnMenu.setVisibility(View.GONE);
        cardNotApproved.setVisibility(View.VISIBLE);
        textStatusBadge.setText(R.string.driver_reg_title);
        String status = application.getStatus();
        if ("draft".equals(status)) {
            textNotApprovedTitle.setText(R.string.driver_home_status_title_draft);
            textNotApprovedDetail.setText(R.string.driver_home_status_detail_draft);
        } else if ("rejected".equals(status)) {
            textNotApprovedTitle.setText(R.string.driver_home_status_title_rejected);
            String reason = application.getRejectionReason();
            if (reason != null && !reason.isEmpty()) {
                textNotApprovedDetail.setText(getString(R.string.driver_home_status_detail_rejected_reason, reason));
            } else {
                textNotApprovedDetail.setText(R.string.driver_home_status_detail_rejected_generic);
            }
        } else if ("suspended".equals(status)) {
            textNotApprovedTitle.setText(R.string.driver_home_status_title_suspended);
            textNotApprovedDetail.setText(R.string.driver_home_status_detail_suspended);
        } else {
            textNotApprovedTitle.setText(R.string.driver_home_status_title_pending_review);
            textNotApprovedDetail.setText(R.string.driver_home_status_detail_pending_review);
        }
    }

    private void loadWallet() {
        driverRepository.getWallet(new ApiCallback<Wallet>() {
            @Override
            public void onSuccess(Wallet wallet) {
                if (!online) {
                    textStatusBadge.setText(
                            String.format(Locale.getDefault(), getString(R.string.driver_home_wallet_balance_format),
                                    wallet.getBalance()));
                }
            }

            @Override
            public void onError(ApiException error) {
                if (!online) {
                    textStatusBadge.setText(R.string.driver_home_wallet_unavailable);
                }
            }
        });
    }

    private void toggleConnection() {
        if (!approved) {
            return;
        }
        if (online) {
            attemptGoOffline();
        } else {
            attemptGoOnline();
        }
    }

    private void attemptGoOnline() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, R.string.driver_home_location_permission_toast, Toast.LENGTH_SHORT).show();
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        LoadingButtonHelper.setLoading(btnConnectToggle, true);
        driverRepository.goOnline(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                LoadingButtonHelper.setLoading(btnConnectToggle, false);
                online = true;
                updateConnectionUi();
                startLocationLoop();
                startInboxListener();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnConnectToggle, false);
                if (error.getCode() == ApiErrorCode.DRIVER_NOT_APPROVED) {
                    Toast.makeText(DriverHomeActivity.this, R.string.driver_home_not_approved_toast,
                            Toast.LENGTH_LONG).show();
                    runApplicationGate();
                } else if (error.getCode() == ApiErrorCode.INSUFFICIENT_BALANCE) {
                    Toast.makeText(DriverHomeActivity.this, R.string.driver_home_insufficient_balance_toast,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(DriverHomeActivity.this, R.string.driver_home_connect_error, Toast.LENGTH_SHORT)
                            .show();
                }
            }
        });
    }

    private void attemptGoOffline() {
        LoadingButtonHelper.setLoading(btnConnectToggle, true);
        driverRepository.goOffline(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                LoadingButtonHelper.setLoading(btnConnectToggle, false);
                online = false;
                updateConnectionUi();
                stopLocationLoop();
                stopInboxListener();
                loadWallet();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnConnectToggle, false);
                Toast.makeText(DriverHomeActivity.this, R.string.driver_home_disconnect_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void updateConnectionUi() {
        if (online) {
            btnConnectToggle.setText(R.string.driver_home_disconnect_button);
            textConnectionStatus.setText(R.string.driver_home_status_online);
            textConnectionStatus.setTextColor(getColor(R.color.drivo_success));
            textStatusBadge.setText(R.string.driver_home_badge_searching);
            radarContainer.setVisibility(View.VISIBLE);
        } else {
            btnConnectToggle.setText(R.string.driver_home_connect_button);
            textConnectionStatus.setText(R.string.driver_home_status_offline);
            textConnectionStatus.setTextColor(getColor(R.color.drivo_on_background));
            radarContainer.setVisibility(View.GONE);
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationLoop() {
        LocationRequest request = new LocationRequest.Builder(LOCATION_INTERVAL_IDLE_MS)
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMinUpdateIntervalMillis(LOCATION_INTERVAL_IDLE_MS)
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
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationLoop() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    private void startInboxListener() {
        String uid = authRepository.getCurrentUserId();
        if (uid == null) {
            return;
        }
        inboxSubscription = realtimeRepository.observeInbox(uid, this::onInboxChanged);
    }

    private void stopInboxListener() {
        if (inboxSubscription != null) {
            inboxSubscription.stop();
            inboxSubscription = null;
        }
    }

    private void onInboxChanged(List<String> rideIds) {
        String rideId = rideIds.isEmpty() ? null : rideIds.get(0);
        if (rideId == null) {
            // El viaje salió del radar (lo tomó otro conductor o venció) — igual que si
            // nunca hubiéramos actuado, según el contrato: "esa oferta se descarta sola".
            displayedRideId = null;
            ignoredRideId = null;
            hideIncomingCard();
            return;
        }
        if (rideId.equals(displayedRideId) || rideId.equals(ignoredRideId)) {
            return;
        }
        fetchIncomingRequest(rideId);
    }

    private void fetchIncomingRequest(String rideId) {
        driverRepository.getIncomingRequest(rideId, new ApiCallback<IncomingRequest>() {
            @Override
            public void onSuccess(IncomingRequest request) {
                showIncomingRequest(request);
            }

            @Override
            public void onError(ApiException error) {
                // Ya no existe o ya no es para nosotros (venció, lo tomaron) — sin pantalla de
                // error, el radar sigue como si nada.
            }
        });
    }

    private void showIncomingRequest(IncomingRequest request) {
        displayedRideId = request.getRideId();
        radarContainer.setVisibility(View.GONE);
        bottomActions.setVisibility(View.GONE);
        cardIncomingRequest.setVisibility(View.VISIBLE);

        textIncomingAvatar.setText(initialsFor(request.getPassengerName()));
        textIncomingName.setText(request.getPassengerName());

        String ratingText = request.getPassengerRating() != null
                ? String.format(Locale.getDefault(), getString(R.string.incoming_request_rating_format),
                        request.getPassengerRating())
                : "";
        if (request.getPassengerTrips() != null && request.getPassengerTrips() == 0) {
            ratingText += getString(R.string.incoming_request_rating_new_suffix);
        }
        textIncomingRating.setText(ratingText);

        textIncomingOffer.setText(String.format(Locale.getDefault(), "$ %.2f", request.getOffer()));
        textIncomingDropoffText.setText(request.getDropoffText());

        if (request.getPickupDistanceM() != null) {
            double km = request.getPickupDistanceM() / 1000.0;
            if (request.getPickupEtaMin() != null) {
                textIncomingPickupDistance.setText(String.format(Locale.getDefault(),
                        getString(R.string.incoming_request_pickup_distance_km_format), km,
                        request.getPickupEtaMin()));
            } else {
                textIncomingPickupDistance.setText(String.format(Locale.getDefault(),
                        getString(R.string.incoming_request_pickup_distance_no_eta_format), km));
            }
            textIncomingPickupDistance.setVisibility(View.VISIBLE);
        } else {
            textIncomingPickupDistance.setVisibility(View.GONE);
        }

        if (request.getTripDistanceM() != null) {
            long tripKm = Math.round(request.getTripDistanceM() / 1000.0);
            textIncomingTripDistance.setText(
                    getString(R.string.incoming_request_trip_distance_format, tripKm));
            textIncomingTripDistance.setVisibility(View.VISIBLE);
        } else {
            textIncomingTripDistance.setVisibility(View.GONE);
        }

        bindCounterOffers(request);

        btnIncomingAccept.setText(getString(R.string.incoming_request_accept_button_format,
                String.format(Locale.getDefault(), "$%.2f", request.getOffer())));
        btnIncomingAccept.setOnClickListener(v -> submitOffer(request.getRideId(), request.getOffer()));

        startIncomingExpiryCountdown(request.getExpiresAt());
    }

    private void bindCounterOffers(IncomingRequest request) {
        containerIncomingCounters.removeAllViews();
        List<Double> increments = request.getCounterIncrements();
        boolean hasIncrements = increments != null && !increments.isEmpty();
        textIncomingCounterLabel.setVisibility(hasIncrements ? View.VISIBLE : View.GONE);
        if (!hasIncrements) {
            return;
        }
        for (Double increment : increments) {
            MaterialButton button = new MaterialButton(this,
                    null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(String.format(Locale.getDefault(),
                    getString(R.string.incoming_request_counter_button_format), increment));
            button.setTextSize(13f);
            button.setAllCaps(false);
            button.setCornerRadius(dpToPx(10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginEnd(dpToPx(6));
            button.setLayoutParams(params);
            double amount = request.getOffer() + increment;
            button.setOnClickListener(v -> submitOffer(request.getRideId(), amount));
            containerIncomingCounters.addView(button);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void submitOffer(String rideId, double amount) {
        // Ya decidimos sobre este viaje (aceptar o contraofertar) — si sigue en la bandeja
        // porque otros conductores todavía están ofertando, no reabrir la misma tarjeta.
        ignoredRideId = rideId;
        hideIncomingCard();
        driverRepository.offerOnRide(rideId, amount, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
            }

            @Override
            public void onError(ApiException error) {
                if (error.getCode() == ApiErrorCode.RIDE_ALREADY_TAKEN) {
                    Toast.makeText(DriverHomeActivity.this, R.string.incoming_request_ride_taken_error,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(DriverHomeActivity.this, R.string.incoming_request_offer_error,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void ignoreIncomingRequest() {
        ignoredRideId = displayedRideId;
        hideIncomingCard();
    }

    private void hideIncomingCard() {
        displayedRideId = null;
        cardIncomingRequest.setVisibility(View.GONE);
        cancelIncomingExpiryTimer();
        if (approved) {
            bottomActions.setVisibility(View.VISIBLE);
            radarContainer.setVisibility(online ? View.VISIBLE : View.GONE);
        }
    }

    private void startIncomingExpiryCountdown(String expiresAtIso) {
        cancelIncomingExpiryTimer();
        Long expiresAtMillis = parseInstantMillis(expiresAtIso);
        if (expiresAtMillis == null) {
            progressIncomingExpiry.setVisibility(View.GONE);
            return;
        }
        long totalMs = expiresAtMillis - System.currentTimeMillis();
        if (totalMs <= 0) {
            progressIncomingExpiry.setVisibility(View.GONE);
            return;
        }
        progressIncomingExpiry.setVisibility(View.VISIBLE);
        progressIncomingExpiry.setMax(1000);
        progressIncomingExpiry.setProgress(1000);
        incomingExpiryTimer = new CountDownTimer(totalMs, 200) {
            @Override
            public void onTick(long millisUntilFinished) {
                progressIncomingExpiry.setProgress((int) (1000 * millisUntilFinished / totalMs));
            }

            @Override
            public void onFinish() {
                progressIncomingExpiry.setProgress(0);
            }
        };
        incomingExpiryTimer.start();
    }

    private void cancelIncomingExpiryTimer() {
        if (incomingExpiryTimer != null) {
            incomingExpiryTimer.cancel();
            incomingExpiryTimer = null;
        }
    }

    private Long parseInstantMillis(String isoTimestamp) {
        if (isoTimestamp == null) {
            return null;
        }
        try {
            return Instant.parse(isoTimestamp).toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
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

    /** Animación puramente decorativa (dos anillos que laten) — mismo patrón de SearchingDriverActivity. */
    private void startRadarPulse() {
        View outer = findViewById(R.id.radar_ring_outer);
        View inner = findViewById(R.id.radar_ring_inner);

        radarAnimator = ValueAnimator.ofFloat(0f, 1f);
        radarAnimator.setDuration(RADAR_PULSE_DURATION_MS);
        radarAnimator.setRepeatCount(ValueAnimator.INFINITE);
        radarAnimator.setInterpolator(new LinearInterpolator());
        radarAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            float scale = 0.85f + fraction * 0.3f;
            outer.setScaleX(scale);
            outer.setScaleY(scale);
            outer.setAlpha(0.2f * (1f - fraction));
            float innerFraction = (fraction + 0.5f) % 1f;
            float innerScale = 0.85f + innerFraction * 0.3f;
            inner.setScaleX(innerScale);
            inner.setScaleY(innerScale);
            inner.setAlpha(0.3f * (1f - innerFraction));
        });
        radarAnimator.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (online) {
            startLocationLoop();
            startInboxListener();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (online) {
            stopLocationLoop();
            stopInboxListener();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (radarAnimator != null) {
            radarAnimator.cancel();
        }
        cancelIncomingExpiryTimer();
    }
}
