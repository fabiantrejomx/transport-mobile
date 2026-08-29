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
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

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
import com.bng.drivo.ui.map.DriverRoutePainter;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.ui.trip.PickupWaitTimer;
import com.bng.drivo.util.ColorUtils;
import com.bng.drivo.util.LoadingButtonHelper;
import com.bng.drivo.util.PlaceTextResolver;
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
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * C4/C7 del flujo de conductor: viaje activo desde MATCHED hasta el cobro y calificación tras
 * COMPLETED. Se abre por el push {@code offer_accepted} (ver DrivoFirebaseMessagingService) o
 * porque DriverHomeActivity la reabre al ver un viaje vivo en {@code GET /driver/current-ride}.
 * El detalle inicial (pasajero, puntos, tarifa) se obtiene de GET /driver/rides/{id}, y el estado
 * real avanza por el mismo canal rides/{id} de Firestore que ya usa ActiveTripActivity del
 * pasajero.
 *
 * <p>Llamar y escribir al pasajero solo existen hasta que el viaje arranca —con él a bordo no hay
 * nada que coordinar— y en DRIVER_ARRIVED aparece el cronómetro de cortesía de 5 min
 * ({@link PickupWaitTimer}), el mismo que el pasajero ve del otro lado.
 *
 * Los 3 pasos del contrato (arrived/start/complete) son intencionalmente 3 taps distintos,
 * aunque el mockup C4 solo tenga un botón "Finalizar Viaje" — CLAUDE.md exige "Llegué al
 * punto" separado de iniciar/finalizar, y son 3 endpoints reales.
 *
 * El mapa sigue esos mismos pasos con un tramo a la vez ({@link DriverRoutePainter}): mientras va
 * por el pasajero se dibuja el tramo conductor→pasajero, y al iniciar el viaje se cambia al tramo
 * origen→destino que pidió el pasajero. Dibujar los dos todo el tiempo mezclaba "lo que me falta
 * manejar ahora" con "lo que viene después".
 *
 * La pantalla sigue el mismo esqueleto que el resto del rediseño: un solo mapa a pantalla
 * completa, S.O.S./Waze flotando arriba y un modal abajo con dos paneles (viaje y cobro). El
 * modal no es arrastrable — ninguno de los dos paneles guarda contenido extra que mostrar.
 *
 * Cancelar (POST /driver/rides/{id}/cancel) solo se ofrece antes de IN_PROGRESS, la misma regla
 * que el pasajero: una vez arrancado el viaje, la única salida es finalizarlo. Salir con "atrás"
 * sigue sin poder retomar la pantalla — el contrato no ofrece cómo, más allá de otro push —, así
 * que "atrás" pide confirmación en vez de cerrar de golpe.
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
    private DriverRoutePainter routePainter;
    @Nullable
    private LatLng lastKnownLocation;
    private String rideId;
    private String currentStatus;
    private boolean terminalStateHandled;
    /**
     * true entre que se toca "Finalizar viaje" y que responde POST /driver/rides/{id}/complete.
     * El canal en vivo (statusSubscription) suele enterarse del COMPLETED antes que esa misma
     * respuesta HTTP llegue —el push va por un canal más rápido que la petición que lo originó—,
     * y sin esta bandera onStatusChanged cerraba la pantalla de golpe (finish()) justo antes de
     * que attemptComplete() alcanzara a pintar el cobro/calificación o recentrar el mapa.
     */
    private boolean completeInFlight;

    private String passengerName;
    private double fare;
    private String pickupText;
    private String dropoffText;
    private LatLng pickupLatLng;
    private LatLng dropoffLatLng;
    private Integer pickupDistanceM;
    private Integer pickupEtaMin;
    private Integer tripDistanceM;
    private final List<LatLng> stops = new ArrayList<>();

    private final List<TextView> cobroStarViews = new ArrayList<>();
    private int cobroRating;

    private View sheetContainer;
    private View sheetContent;
    private BottomSheetBehavior<View> sheetBehavior;
    private int lastSheetHeightPx = -1;
    private int sheetTopInsetPx;

    private View panelTrip;
    private TextView textTripAvatar;
    private TextView textTripPassengerName;
    private TextView textTripPassengerRating;
    private TextView textTripStatusBadge;
    private TextView textTripActionTitle;
    private TextView textTripDestinationLabel;
    private TextView textTripActionSubtitle;
    private TextView textTripFare;
    private View tileTripSecondaryStat;
    private TextView textTripSecondaryStatLabel;
    private TextView textTripSecondaryStatValue;
    private MaterialButton btnTripAction;
    private MaterialButton btnTripCancel;
    private View btnTripCall;
    private View btnTripMessage;
    private PickupWaitTimer waitTimer;
    /** Evita repetir GET /driver/current-ride: showPickupPhase se repinta varias veces por fase. */
    private boolean waitAnchorRequested;

    private View panelCobro;
    private TextView textCobroAmount;
    private TextView textCobroCommissionNote;
    private TextView textCobroRatingPrompt;
    private LinearLayout containerCobroStars;
    private MaterialButton btnCobroClose;
    private OnBackPressedCallback backCallback;

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
        routePainter = new DriverRoutePainter(this);

        sheetContainer = findViewById(R.id.sheet_container);
        sheetContent = findViewById(R.id.sheet_content);
        panelTrip = findViewById(R.id.panel_driver_trip);
        panelCobro = findViewById(R.id.panel_driver_cobro);
        textTripAvatar = findViewById(R.id.text_trip_avatar);
        textTripPassengerName = findViewById(R.id.text_trip_passenger_name);
        textTripPassengerRating = findViewById(R.id.text_trip_passenger_rating);
        textTripStatusBadge = findViewById(R.id.text_trip_status_badge);
        textTripActionTitle = findViewById(R.id.text_trip_action_title);
        textTripDestinationLabel = findViewById(R.id.text_trip_destination_label);
        textTripActionSubtitle = findViewById(R.id.text_trip_action_subtitle);
        textTripFare = findViewById(R.id.text_trip_fare);
        tileTripSecondaryStat = findViewById(R.id.tile_trip_secondary_stat);
        textTripSecondaryStatLabel = findViewById(R.id.text_trip_secondary_stat_label);
        textTripSecondaryStatValue = findViewById(R.id.text_trip_secondary_stat_value);
        btnTripAction = findViewById(R.id.btn_trip_action);
        btnTripCancel = findViewById(R.id.btn_trip_cancel);
        btnTripCall = findViewById(R.id.btn_trip_call);
        btnTripMessage = findViewById(R.id.btn_trip_message);
        waitTimer = new PickupWaitTimer(panelTrip, R.string.driver_trip_wait_label,
                R.string.driver_trip_wait_hint, R.string.driver_trip_wait_expired);
        textCobroAmount = findViewById(R.id.text_cobro_amount);
        textCobroCommissionNote = findViewById(R.id.text_cobro_commission_note);
        textCobroRatingPrompt = findViewById(R.id.text_cobro_rating_prompt);
        containerCobroStars = findViewById(R.id.container_cobro_stars);
        btnCobroClose = findViewById(R.id.btn_cobro_close);

        findViewById(R.id.btn_trip_sos).setOnClickListener(v -> sendSos());
        findViewById(R.id.btn_trip_waze).setOnClickListener(v -> openWaze());
        btnTripCancel.setOnClickListener(v -> confirmCancelTrip());
        btnCobroClose.setOnClickListener(v -> submitRating());
        // Llamada y chat dentro de la app todavía no existen en el contrato; el aviso es honesto
        // en vez de un botón muerto.
        btnTripCall.setOnClickListener(v -> showContactComingSoon());
        btnTripMessage.setOnClickListener(v -> showContactComingSoon());
        setUpCobroStars();
        setUpBottomSheet();
        setUpBackHandling();

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
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.setPadding(0, sheetTopInsetPx, 0, Math.max(lastSheetHeightPx, 0));
        if (hasLocationPermission()) {
            googleMap.setMyLocationEnabled(true);
        }
        Fragment mapFragment = getSupportFragmentManager().findFragmentById(R.id.map);
        routePainter.attach(googleMap, mapFragment != null ? mapFragment.getView() : null);
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
                stops.clear();
                for (com.bng.drivo.data.model.Waypoint stop : request.getStops()) {
                    stops.add(new LatLng(stop.getLat(), stop.getLng()));
                }
                // "Tu ubicación actual" es el placeholder del pasajero y aquí no dice nada; se
                // cambia por la dirección real del punto de recogida (ver PlaceTextResolver).
                PlaceTextResolver.resolve(DriverActiveTripActivity.this, pickupText, pickupLatLng,
                        resolved -> {
                            pickupText = resolved;
                            if (!"IN_PROGRESS".equals(currentStatus) && passengerName != null) {
                                showPickupPhase("DRIVER_ARRIVED".equals(currentStatus));
                            }
                        });

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

    /**
     * Un tramo a la vez, según la fase: el de recogida hasta que el viaje arranca, y el del
     * pasajero a partir de IN_PROGRESS. Se llama en cada cambio de fase y cuando el modal cambia
     * de alto, para reencuadrar contra el hueco visible que quede.
     */
    private void drawTripMap() {
        if (googleMap == null || pickupLatLng == null || !routePainter.isReady()) {
            return;
        }
        if ("IN_PROGRESS".equals(currentStatus) && dropoffLatLng != null) {
            routePainter.showTripLeg(pickupLatLng, stops, dropoffLatLng);
            return;
        }
        routePainter.showPickupLeg(lastKnownLocation, pickupLatLng);
        if (lastKnownLocation == null) {
            requestLastLocation();
        }
    }

    /** Primera posición de la sesión: sin ella el tramo de recogida saldría sin punto de partida. */
    @SuppressLint("MissingPermission")
    private void requestLastLocation() {
        if (!hasLocationPermission()) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null || googleMap == null) {
                return;
            }
            lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());
            if (!"IN_PROGRESS".equals(currentStatus) && pickupLatLng != null && routePainter.isReady()) {
                routePainter.showPickupLeg(lastKnownLocation, pickupLatLng);
            }
        });
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
                if (completeInFlight) {
                    // attemptComplete() ya está esperando esa misma respuesta HTTP, que es quien
                    // debe pintar el cobro real (trae la comisión) y recentrar el mapa — este
                    // push solo se adelantó por ir en un canal más rápido. Nada que hacer aquí.
                    break;
                }
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
        showPanel(panelTrip);
        // Antes de arrancar el viaje todavía se puede cancelar (POST /driver/rides/{id}/cancel).
        btnTripCancel.setVisibility(View.VISIBLE);
        // Llamar y escribir al pasajero solo sirven mientras no está en el coche.
        btnTripCall.setVisibility(View.VISIBLE);
        btnTripMessage.setVisibility(View.VISIBLE);

        if (arrived) {
            startWaitCountdown();
        } else {
            waitTimer.stop();
        }

        // El badge y la etiqueta "Destino" son solo de IN_PROGRESS — aquí el tile de abajo ya
        // hace ese trabajo con datos reales ("Llegada en").
        textTripStatusBadge.setVisibility(View.GONE);
        textTripDestinationLabel.setVisibility(View.GONE);

        textTripActionTitle.setText(getString(R.string.driver_trip_pickup_title_format, passengerName));
        double pickupKm = pickupDistanceM != null ? pickupDistanceM / 1000.0 : 0;
        textTripActionSubtitle.setText(
                getString(R.string.driver_trip_pickup_subtitle_format, pickupText, pickupKm));

        tileTripSecondaryStat.setVisibility(View.VISIBLE);
        textTripSecondaryStatLabel.setText(R.string.driver_trip_eta_stat_label);
        textTripSecondaryStatValue.setText(pickupEtaMin != null
                ? getString(R.string.searching_eta_min, pickupEtaMin) : "--");

        drawTripMap();
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
        showPanel(panelTrip);
        // Ya arrancó: la única salida es finalizarlo, igual que del lado del pasajero.
        btnTripCancel.setVisibility(View.GONE);
        // El pasajero ya va a bordo: no hay a quién llamar ni nada que coordinar por escrito.
        btnTripCall.setVisibility(View.GONE);
        btnTripMessage.setVisibility(View.GONE);
        waitTimer.stop();

        // El estado ("En curso") se muestra junto al nombre, no ya en el tile de abajo.
        textTripStatusBadge.setText(R.string.driver_trip_progress_stat_value);
        textTripStatusBadge.setVisibility(View.VISIBLE);
        textTripDestinationLabel.setVisibility(View.VISIBLE);

        textTripActionTitle.setText(getString(R.string.driver_trip_dropoff_title_format, passengerName));
        long tripKm = tripDistanceM != null ? Math.round(tripDistanceM / 1000.0) : 0;
        textTripActionSubtitle.setText(
                getString(R.string.driver_trip_dropoff_subtitle_format, dropoffText, tripKm));

        // El tile queda listo para el ETA real del tramo (label ya puesto), pero sin ese dato
        // todavía se oculta en vez de mostrar algo vacío o inventado — ver
        // driver_trip_dropoff_eta_stat_label.
        textTripSecondaryStatLabel.setText(R.string.driver_trip_dropoff_eta_stat_label);
        tileTripSecondaryStat.setVisibility(View.GONE);

        btnTripAction.setText(R.string.driver_trip_action_complete);
        btnTripAction.setEnabled(true);
        btnTripAction.setOnClickListener(v -> attemptComplete());

        drawTripMap();
    }

    private void showCobroRatingPhase(Ride completedRide) {
        showPanel(panelCobro);
        waitTimer.stop();

        double agreedFare = completedRide.getAgreedFare() != null ? completedRide.getAgreedFare() : fare;
        textCobroAmount.setText(String.format(Locale.getDefault(), "$%.0f", agreedFare));

        Double commission = completedRide.getCommission();
        textCobroCommissionNote.setText(commission != null
                ? getString(R.string.driver_cobro_commission_note_format, commission) : "");
        textCobroRatingPrompt.setText(getString(R.string.driver_cobro_rating_prompt_format, passengerName));
    }

    /**
     * Los 5 min de cortesía que el pasajero tiene para salir. El ancla es {@code driver_arrived_at}
     * —la hora que el servidor guardó al validar por GPS esta misma llegada—, no el momento del
     * tap: así el conductor y el pasajero cuentan lo mismo, y volver a abrir la pantalla a media
     * espera no regala minutos. Se pide por {@code GET /driver/current-ride} porque sirve para los
     * dos casos, el tap y la pantalla reabierta.
     */
    private void startWaitCountdown() {
        waitTimer.start(null);
        if (waitAnchorRequested) {
            return;
        }
        waitAnchorRequested = true;
        driverRepository.getCurrentRide(new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (ride == null || !rideId.equals(ride.getId())
                        || !"DRIVER_ARRIVED".equals(currentStatus)) {
                    return;
                }
                waitTimer.start(ride.getDriverArrivedAt());
            }

            @Override
            public void onError(ApiException error) {
                // Se queda con el ancla local que ya arrancó arriba.
                waitAnchorRequested = false;
            }
        });
    }

    /** Cambia el panel visible del modal y deja que se remida solo en el siguiente pase. */
    private void showPanel(View panel) {
        panelTrip.setVisibility(panel == panelTrip ? View.VISIBLE : View.GONE);
        panelCobro.setVisibility(panel == panelCobro ? View.VISIBLE : View.GONE);
        lastSheetHeightPx = -1;
    }

    private void showContactComingSoon() {
        Toast.makeText(this, R.string.driver_trip_contact_coming_soon, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------------------------------
    // Cancelar el viaje
    // ---------------------------------------------------------------------------------------

    /**
     * Cancelar tiene consecuencias reales para el pasajero (vuelve a buscar conductor) y para la
     * cuenta del conductor, así que nunca se ejecuta con un solo toque: el diálogo dice qué pasa
     * antes de confirmar.
     */
    private void confirmCancelTrip() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.driver_trip_cancel_title)
                .setMessage(R.string.driver_trip_cancel_message)
                .setPositiveButton(R.string.driver_trip_cancel_positive, (dialog, which) -> cancelTrip())
                .setNegativeButton(R.string.driver_trip_cancel_negative, null)
                .show();
    }

    private void cancelTrip() {
        LoadingButtonHelper.setLoading(btnTripCancel, true);
        driverRepository.cancelRide(rideId, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride result) {
                terminalStateHandled = true;
                stopLocationLoop();
                Toast.makeText(DriverActiveTripActivity.this, R.string.driver_trip_cancelled_toast,
                        Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnTripCancel, false);
                Toast.makeText(DriverActiveTripActivity.this, R.string.driver_trip_cancel_error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * "Atrás" no puede cerrar la pantalla sin más: salir por accidente dejaría al conductor con un
     * viaje asignado y con el pasajero esperando. Antes de IN_PROGRESS ofrece cancelar de verdad;
     * después no hace nada, porque a partir de ahí el único desenlace es terminarlo.
     *
     * <p>Retomarla ya no es el problema que era —{@code GET /driver/current-ride} la reabre al
     * volver a Inicio—, pero eso arregla el accidente, no lo justifica: irse a medio viaje sigue
     * sin ser una salida.
     */
    private void setUpBackHandling() {
        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (btnTripCancel.getVisibility() == View.VISIBLE) {
                    confirmCancelTrip();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    // ---------------------------------------------------------------------------------------
    // Modal y medidas (mismo esquema que DriverHomeActivity)
    // ---------------------------------------------------------------------------------------

    private void setUpBottomSheet() {
        sheetBehavior = BottomSheetBehavior.from(sheetContainer);
        sheetBehavior.setHideable(false);
        sheetBehavior.setSkipCollapsed(false);
        sheetBehavior.setDraggable(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::updateSheetStops;
        sheetContent.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    private void updateSheetStops() {
        applyTopInsets();
        int heightPx = sheetContent.getHeight();
        if (heightPx <= 0 || heightPx == lastSheetHeightPx) {
            return;
        }
        lastSheetHeightPx = heightPx;
        sheetBehavior.setPeekHeight(heightPx, true);
        // El mapa encuadra la ruta contra el rectángulo que queda a la vista, no contra la
        // pantalla completa: sin esto los pines caen detrás del modal.
        if (googleMap != null) {
            googleMap.setPadding(0, sheetTopInsetPx, 0, heightPx);
            drawTripMap();
        }
    }

    private void applyTopInsets() {
        View root = findViewById(android.R.id.content);
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
        if (insets == null) {
            return;
        }
        int topInsetPx = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top;
        sheetTopInsetPx = topInsetPx;
        int bottomInsetPx = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        if (sheetContent.getPaddingBottom() != bottomInsetPx) {
            sheetContent.setPadding(sheetContent.getPaddingLeft(), sheetContent.getPaddingTop(),
                    sheetContent.getPaddingRight(), bottomInsetPx);
        }

        int availableHeightPx = root.getHeight() - topInsetPx;
        ViewGroup.LayoutParams sheetParams = sheetContainer.getLayoutParams();
        if (root.getHeight() > 0 && sheetParams.height != availableHeightPx) {
            sheetParams.height = availableHeightPx;
            sheetContainer.setLayoutParams(sheetParams);
        }

        int marginPx = Math.round(16 * getResources().getDisplayMetrics().density);
        setTopMargin(findViewById(R.id.btn_trip_sos), topInsetPx + marginPx);
        setTopMargin(findViewById(R.id.btn_trip_waze), topInsetPx + marginPx);
    }

    private void setTopMargin(View view, int topMarginPx) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.topMargin != topMarginPx) {
            params.topMargin = topMarginPx;
            view.setLayoutParams(params);
        }
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
        completeInFlight = true;
        driverRepository.completeRide(rideId, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride result) {
                completeInFlight = false;
                LoadingButtonHelper.setLoading(btnTripAction, false);
                terminalStateHandled = true;
                currentStatus = "COMPLETED";
                stopLocationLoop();
                showCobroRatingPhase(result);
                // La ruta del viaje que se acaba de cerrar ya no importa: el mapa vuelve a
                // centrarse en el conductor, como al entrar a Home, en vez de quedarse en el
                // último encuadre del tramo recién terminado.
                recenterMapOnDriver();
            }

            @Override
            public void onError(ApiException error) {
                completeInFlight = false;
                LoadingButtonHelper.setLoading(btnTripAction, false);
                Toast.makeText(DriverActiveTripActivity.this, R.string.driver_trip_complete_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void recenterMapOnDriver() {
        if (googleMap == null) {
            return;
        }
        if (lastKnownLocation != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastKnownLocation, 16f));
            return;
        }
        if (!hasLocationPermission()) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null || googleMap == null) {
                return;
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()), 16f));
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
            public void onLocationResult(@NonNull LocationResult result) {
                android.location.Location location = result.getLastLocation();
                if (location == null) {
                    return;
                }
                lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());
                // Solo mueve el coche: reencuadrar cada 4.5 s le quitaría al conductor el control
                // de la cámara mientras maneja.
                if (routePainter.isReady()) {
                    routePainter.updateDriverPosition(lastKnownLocation);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Pueden ser null: sin rideId, onCreate se va por finish() antes de construirlos.
        if (routePainter != null) {
            routePainter.detach();
        }
        if (waitTimer != null) {
            waitTimer.stop();
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
}
