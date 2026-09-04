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
import com.google.android.gms.maps.model.CameraPosition;
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
    /**
     * Dónde está el conductor, según la pantalla de la que se viene.
     *
     * <p>Viaja en el Intent para poder centrar el mapa <b>en el mismo instante</b> en que existe,
     * sin esperar a nadie. Pedirle la ubicación al sistema aquí también funciona, pero es una
     * llamada asíncrona, y el hueco entre que el mapa aparece y esa llamada contesta es justo el
     * rato en que se ve el mundo entero centrado en el golfo de Guinea. Quien nos abre ya tiene el
     * dato fresco —lleva un bucle de ubicación corriendo—, así que basta con pasarlo.
     *
     * <p>Puede no venir (al abrir desde una notificación no hay pantalla previa que lo tenga), y
     * entonces se cae a la llamada asíncrona.
     */
    public static final String EXTRA_DRIVER_LAT = "extra_driver_lat";
    public static final String EXTRA_DRIVER_LNG = "extra_driver_lng";

    private static final long LOCATION_INTERVAL_TRIP_MS = 4500L;
    /** El mismo con el que DriverHomeActivity centra al conductor: se viene de esa pantalla. */
    private static final float SELF_ZOOM = 16f;
    /** Aire entre los mandos de cámara y el borde superior del modal. */
    private static final int FOLLOW_BUTTON_GAP_DP = 12;
    /** Vista de conducción: se leen los nombres de calle y se ve el siguiente cruce. */
    private static final float NAVIGATION_ZOOM = 18f;
    private static final float NAVIGATION_TILT = 45f;
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
    /**
     * Trazo por calles del viaje del pasajero, codificado. No viene en {@code GET /driver/rides/{id}}
     * —el DTO de la solicitud entrante no lo lleva—, así que se pide aparte a
     * {@code GET /driver/current-ride}, que sí lo trae. Null mientras no llega o si el servidor no
     * lo mandó: entonces el tramo se pinta como guía recta.
     */
    @Nullable
    private String tripPolyline;
    /**
     * ETA publicado por el servidor, en minutos. El conductor lo lee del canal en vivo aunque su
     * propia posición sea la que lo genera: el número lo calcula el servidor a propósito, para que
     * él y el pasajero vean exactamente el mismo, y no dos cuentas parecidas.
     */
    @Nullable
    private Integer etaMin;
    /** Último rumbo del GPS, en grados. Null parado: a velocidad cero el dato es ruido. */
    @Nullable
    private Double lastBearing;
    /**
     * Dónde terminó el viaje. Se fija al cerrarlo y ya no cambia.
     *
     * <p>Existe porque {@link #lastKnownLocation} es un campo vivo que escriben varias fuentes
     * —el bucle de ubicación, las lecturas puntuales antes de llegar y de cerrar, el extra con el
     * que se abre la pantalla—, y cualquiera de ellas puede escribirlo <em>después</em> de que la
     * cámara ya se colocó. Con el viaje cerrado el sitio correcto es uno solo y no vuelve a
     * moverse: el que el servidor acaba de validar contra el destino. Guardarlo aparte es lo que
     * hace que el encuadre final no dependa de quién escriba último.
     */
    @Nullable
    private LatLng tripEndLocation;
    /**
     * Si {@link #lastKnownLocation} viene del flujo de ubicación y no del extra con el que se abrió
     * la pantalla. La distinción importa donde el dato decide algo: ese extra es dónde estaba el
     * conductor <b>al recibir el viaje</b>, y mandárselo al servidor como "dónde estoy ahora" le
     * haría rechazar una llegada o un cierre que sí eran válidos.
     */
    private boolean hasLiveFix;
    @Nullable
    private RealtimeSubscription locationSubscription;
    /**
     * Si la cámara va pegada al coche. Arranca encendido y <b>lo apaga el propio conductor</b> con
     * solo arrastrar el mapa: mirar una calle de más adelante no debe pelearse con una cámara que
     * lo devuelve a su sitio cada 4.5 s. El botón flotante lo vuelve a encender.
     */
    private boolean followingDriver = true;
    /**
     * Vista de conducción: más cerca, inclinada y girada hacia donde avanza. Solo con el viaje en
     * curso. Cambia lo que hace el seguimiento, no si sigue: en esta vista la cámara también gira
     * con el rumbo, y fuera de ella solo se desplaza.
     */
    private boolean navigationView;
    private View mapControls;
    private View btnFollowDriver;
    private View btnNavigationView;
    private View btnFrameRoute;

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
        // Antes de crear el mapa: onMapReady se apoya en esto para centrar sin esperas.
        if (getIntent().hasExtra(EXTRA_DRIVER_LAT)) {
            lastKnownLocation = new LatLng(
                    getIntent().getDoubleExtra(EXTRA_DRIVER_LAT, 0),
                    getIntent().getDoubleExtra(EXTRA_DRIVER_LNG, 0));
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

        mapControls = findViewById(R.id.container_map_controls);
        btnFollowDriver = findViewById(R.id.btn_follow_driver);
        btnFollowDriver.setOnClickListener(v -> followDriverAgain());
        btnNavigationView = findViewById(R.id.btn_navigation_view);
        btnNavigationView.setOnClickListener(v -> showNavigationView());
        btnFrameRoute = findViewById(R.id.btn_frame_route);
        btnFrameRoute.setOnClickListener(v -> showFullRoute());
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
        // El coche propio (dibujado por routePainter en cada fase) reemplaza al punto azul del
        // SDK — igual que en DriverHomeActivity.
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.setPadding(0, sheetTopInsetPx, 0, Math.max(lastSheetHeightPx, 0));
        Fragment mapFragment = getSupportFragmentManager().findFragmentById(R.id.map);
        routePainter.attach(googleMap, mapFragment != null ? mapFragment.getView() : null);
        // Solo el gesto del conductor suelta la cámara. Los movimientos que hace la app —encuadrar
        // la ruta, centrarse en él— llegan aquí con otro motivo y no deben apagar el seguimiento.
        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                followingDriver = false;
            }
        });
        centerOnDriver();
        drawTripMap();
    }

    /**
     * Deja la cámara sobre el conductor en cuanto el mapa existe.
     *
     * <p>Sin esto la pantalla abría en la posición por defecto del SDK —latitud 0, longitud 0: el
     * golfo de Guinea— y se quedaba ahí <em>todo lo que tardara la red</em>, porque el primer
     * encuadre no llega hasta que {@code GET /driver/rides/{id}} responde con el punto de recogida.
     * Al responder, la cámara saltaba de medio océano Atlántico a Orizaba de un tirón. El salto no
     * lo causaba una animación mal puesta: lo causaba que nadie había dicho dónde mirar.
     *
     * <p>Se centra al mismo zoom 16 y sobre el mismo punto que dejó {@link DriverHomeActivity}, así
     * que el mapa se abre viéndose igual que aquel del que se viene, y el único movimiento que
     * queda es el de abrirse para que quepa el tramo de recogida.
     *
     * <p>La ubicación puede llegar tarde y cruzarse con la respuesta de la red. Si para entonces la
     * ruta ya está dibujada, manda ella: recentrar sobre el conductor desharía el encuadre que
     * acaba de hacerse, que es justo el problema al revés.
     */
    private void centerOnDriver() {
        if (lastKnownLocation != null) {
            moveToDriver();
            return;
        }
        if (!hasLocationPermission()) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null || googleMap == null) {
                return;
            }
            lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());
            if (pickupLatLng == null) {
                moveToDriver();
            }
        });
    }

    /**
     * El botón flotante: vuelve al coche y reengancha la cámara.
     *
     * <p>Fija el zoom, a diferencia del seguimiento continuo: quien lo toca se ha perdido de vista
     * a sí mismo, y devolverlo al mismo zoom desde el que se fue no lo ayudaría.
     */
    /**
     * Enciende o apaga la vista de conducción, y con ella el marcador que le corresponde: la flecha
     * mientras el mapa va inclinado, el coche cuando vuelve a estar plano.
     */
    private void setNavigationView(boolean enabled) {
        navigationView = enabled;
        if (routePainter.isReady()) {
            routePainter.setNavigationMode(enabled);
        }
    }

    /**
     * Dónde debe mirar la cámara. Con el viaje cerrado manda {@link #tripEndLocation}, que ya no se
     * mueve; mientras está en curso, la última posición conocida.
     */
    /**
     * La posición más reciente que tiene la app, o null si no hay ninguna de fiar.
     *
     * <p>Solo devuelve la del flujo en vivo. {@code getLastLocation()} da "la mejor disponible",
     * que el sistema cachea y puede ser de hace rato; con un flujo de alta precisión entregando
     * cada 4.5 s, lo que ya tenemos es más fresco que lo que devolvería esa consulta.
     */
    @Nullable
    private LatLng liveLocation() {
        return hasLiveFix ? lastKnownLocation : null;
    }

    @Nullable
    private LatLng cameraTarget() {
        return tripEndLocation != null ? tripEndLocation : lastKnownLocation;
    }

    private void followDriverAgain() {
        followingDriver = true;
        setNavigationView(false);
        LatLng destino = cameraTarget();
        if (googleMap == null || destino == null) {
            return;
        }
        // Se endereza el mapa al volver: si venía de la vista de navegación seguiría inclinado y
        // girado, y esta vista es la de "ver dónde estoy", que se lee mejor plana y al norte.
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder()
                        .target(destino).zoom(SELF_ZOOM).tilt(0f).bearing(0f).build()));
    }

    /**
     * Vista de conducción: pegada al coche, inclinada y girada hacia donde avanza.
     *
     * <p>Es la que sirve para manejar, no para ubicarse: a este acercamiento se leen los nombres de
     * las calles y se ve el siguiente cruce, que es lo que el conductor mira mientras va.
     *
     * <p>El giro sale del rumbo del GPS y solo se aplica cuando lo hay. Parado no viene, y forzarlo
     * haría girar el mapa entero con cada temblor de la señal en un semáforo.
     */
    private void showNavigationView() {
        followingDriver = true;
        setNavigationView(true);
        if (googleMap == null || lastKnownLocation == null) {
            return;
        }
        CameraPosition.Builder camara = new CameraPosition.Builder()
                .target(lastKnownLocation)
                .zoom(NAVIGATION_ZOOM)
                .tilt(NAVIGATION_TILT);
        if (lastBearing != null) {
            camara.bearing(lastBearing.floatValue());
        }
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(camara.build()));
    }

    /**
     * El recorrido entero en pantalla, como se veía antes de arrancar.
     *
     * <p>Suelta el seguimiento a propósito: es una vista de conjunto, y dejar la cámara enganchada
     * al coche la desharía en cuanto avanzara unos metros. Se recupera con cualquiera de los otros
     * dos botones.
     */
    private void showFullRoute() {
        followingDriver = false;
        setNavigationView(false);
        if (googleMap == null || pickupLatLng == null || dropoffLatLng == null
                || !routePainter.isReady()) {
            return;
        }
        // Encuadrar por límites deja el mapa plano y al norte por sí solo, así que no hace falta
        // enderezarlo aparte: dos animaciones seguidas solo se pisarían.
        routePainter.showTripLeg(lastKnownLocation, pickupLatLng, stops, dropoffLatLng,
                tripPolyline, true);
    }

    private void moveToDriver() {
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastKnownLocation, SELF_ZOOM));
        // A partir de aquí el encuadre de la ruta puede animar: ya no saldría de la nada, sino de
        // donde está el conductor, y verlo abrirse dice más que aparecer ya abierto.
        routePainter.markCameraPositioned();
    }

    /**
     * Segundo intento de conseguir el trazo, solo si el detalle de la solicitud vino sin él.
     *
     * <p>Desde el contrato 1.7.0 {@code GET /driver/rides/{id}} ya lo incluye y esto no llega a
     * ejecutarse; sigue aquí porque {@code GET /driver/current-ride} lo trae desde 1.6.0 y cubre al
     * servidor que aún no se haya actualizado. Es información de pintura: si también falla, no se
     * avisa de nada y el mapa se queda con la guía recta.
     *
     * <p>El tramo de recogida —del conductor al pasajero— no entra aquí y sigue siendo recta a
     * propósito: el servidor no lo calcula con Google, su ETA sale de la línea recta por un factor
     * de calle.
     */
    private void fetchTripPolyline() {
        driverRepository.getCurrentRide(new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (ride == null || !rideId.equals(ride.getId()) || ride.getPolyline() == null) {
                    return;
                }
                tripPolyline = ride.getPolyline();
                drawTripMap();
            }

            @Override
            public void onError(ApiException error) {
                // Sin trazo: el mapa se queda con la guía recta, que es el respaldo previsto.
            }
        });
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
                        ? getString(R.string.rating_star_format, request.getPassengerRating()) : "");
                textTripFare.setText(String.format(Locale.getDefault(), "$%.2f", fare));

                tripPolyline = request.getPolyline();
                drawTripMap();
                if (tripPolyline == null) {
                    fetchTripPolyline();
                }
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
        // Con el viaje ya cerrado no hay nada que redibujar, y volver a entrar aquí era un salto:
        // el estado deja de ser IN_PROGRESS, así que la rama de abajo caía en el tramo de recogida
        // y llevaba la cámara desde el destino —donde el conductor acaba de dejar al pasajero—
        // hasta el punto donde lo recogió, a kilómetros de distancia. No lo disparaba nadie a
        // propósito: al cambiar al panel de cobro, el modal cambia de alto y updateSheetStops()
        // repinta el mapa.
        // El estado también se mira, y no solo la bandera: "COMPLETED" llega por Firestore, que es
        // más rápido que la respuesta HTTP, así que hay un instante en que el viaje ya terminó y
        // terminalStateHandled todavía no se ha puesto.
        if (terminalStateHandled || "COMPLETED".equals(currentStatus)) {
            return;
        }
        if ("IN_PROGRESS".equals(currentStatus) && dropoffLatLng != null) {
            // Sin encuadrar la ruta: ya arrancó y lo que el conductor necesita ver es la calle que
            // tiene delante, no el viaje entero desde arriba. El encuadre completo servía para
            // decidir si le convenía; a partir de aquí solo aleja la cámara de donde va. Lo centra
            // showInProgressPhase() una vez, y el seguimiento lo mantiene.
            routePainter.showTripLeg(lastKnownLocation, pickupLatLng, stops, dropoffLatLng,
                    tripPolyline, false);
        } else {
            routePainter.showPickupLeg(lastKnownLocation, pickupLatLng);
        }
        // Sin esto el coche del conductor no aparecería hasta la siguiente lectura del loop de
        // ubicación (unos segundos) — con la misma primera posición de sesión que ya usaba el
        // tramo de recogida, ahora también para el tramo del viaje.
        if (lastKnownLocation == null) {
            requestLastLocation();
        }
    }

    /** Primera posición de la sesión: sin ella el mapa saldría sin el coche del conductor. */
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
            drawTripMap();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        statusSubscription = realtimeRepository.observeRideStatus(rideId, this::onStatusChanged);
        locationSubscription = realtimeRepository.observeDriverLocation(rideId,
                (lat, lng, eta) -> {
                    etaMin = eta;
                    bindEta();
                });
        startLocationLoop();
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

        textTripSecondaryStatLabel.setText(R.string.driver_trip_eta_stat_label);
        bindEta();

        btnNavigationView.setVisibility(View.GONE);
        btnFrameRoute.setVisibility(View.GONE);

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

        // El dato que le faltaba a este tile ya existe: el servidor publica el ETA contra el
        // destino en cuanto el viaje arranca. Sigue ocultándose si no viene, que era el motivo
        // original de esconderlo — enseñar un hueco donde dice "Llegada estimada" prometería algo.
        textTripSecondaryStatLabel.setText(R.string.driver_trip_dropoff_eta_stat_label);
        bindEta();

        // Los dos mandos extra son de este paso: antes no hay recorrido que encuadrar ni nada
        // que navegar.
        btnNavigationView.setVisibility(View.VISIBLE);
        btnFrameRoute.setVisibility(View.VISIBLE);

        // Entrar al viaje lo pone directamente en vista de conducción, aunque hubiera soltado la
        // cámara mirando el mapa mientras esperaba: empieza a manejar y esa es la que le sirve.
        showNavigationView();

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

    /**
     * El tiempo que falta, en el tile de abajo. La etiqueta la pone cada fase —"Llegada en" yendo
     * por el pasajero, "Llegada estimada" ya en el viaje—; aquí solo va el número.
     *
     * <p>Mientras el canal en vivo no ha dado el primero se usa el de la solicitud, que es la misma
     * cuenta hecha en el servidor cuando el viaje se ofreció: así el tile no arranca vacío. Sin
     * ninguno de los dos, el tile se esconde en vez de enseñar un guion: la etiqueta promete un
     * dato y es mejor no ponerla que ponerla en falso.
     */
    private void bindEta() {
        Integer minutos = etaMin != null ? etaMin
                : ("IN_PROGRESS".equals(currentStatus) ? null : pickupEtaMin);
        tileTripSecondaryStat.setVisibility(minutos != null ? View.VISIBLE : View.GONE);
        if (minutos != null) {
            textTripSecondaryStatValue.setText(getString(R.string.eta_approx_min, minutos));
        }
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
        // El modal cambia de alto con la fase (el cronómetro de cortesía lo estira, el panel de
        // cobro más), así que el botón se recoloca con él en vez de llevar un margen fijo.
        ViewGroup.MarginLayoutParams controlParams =
                (ViewGroup.MarginLayoutParams) mapControls.getLayoutParams();
        int margenPx = heightPx + Math.round(
                FOLLOW_BUTTON_GAP_DP * getResources().getDisplayMetrics().density);
        if (controlParams.bottomMargin != margenPx) {
            controlParams.bottomMargin = margenPx;
            mapControls.setLayoutParams(controlParams);
        }
        // El mapa encuadra la ruta contra el rectángulo que queda a la vista, no contra la
        // pantalla completa: sin esto los pines caen detrás del modal.
        if (googleMap != null) {
            googleMap.setPadding(0, sheetTopInsetPx, 0, heightPx);
            if (terminalStateHandled || "COMPLETED".equals(currentStatus)) {
                // El viaje ya cerró y no hay nada que redibujar, pero el hueco visible sí acaba de
                // cambiar: el panel de cobro es mucho más alto que el del viaje. El centrado que
                // hizo attemptComplete() se calculó contra el hueco anterior, así que el conductor
                // quedaba descentrado —o detrás del propio panel— y había que buscarlo a mano.
                // El padding manda sobre la cámara, y este es el primer momento en que es el bueno.
                recenterMapOnDriver();
            } else {
                drawTripMap();
            }
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
        // Mismo criterio que al cerrar: el servidor comprueba esta posición contra el punto de
        // recogida, así que se manda la del flujo en vivo antes que la cacheada del sistema.
        LatLng enVivo = liveLocation();
        if (enVivo != null) {
            sendArrived(enVivo.latitude, enVivo.longitude);
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        LoadingButtonHelper.setLoading(btnTripAction, false);
                        Toast.makeText(this, R.string.driver_trip_arrived_error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendArrived(location.getLatitude(), location.getLongitude());
                })
                .addOnFailureListener(e -> {
                    LoadingButtonHelper.setLoading(btnTripAction, false);
                    Toast.makeText(this, R.string.driver_trip_arrived_error, Toast.LENGTH_SHORT).show();
                });
    }

    private void sendArrived(double lat, double lng) {
        driverRepository.markArrived(rideId, lat, lng, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride result) {
                LoadingButtonHelper.setLoading(btnTripAction, false);
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnTripAction, false);
                showProximityAwareError(error, ApiErrorCode.TOO_FAR_FROM_PICKUP,
                        R.string.driver_trip_arrived_error);
            }
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

    /**
     * Finalizar el viaje. Como marcar llegada, va con la posición: el servidor comprueba que el
     * conductor esté cerca del destino antes de dejarlo cerrar, y de las dos comprobaciones es la
     * que más pesa —cerrar cobra la comisión y da el trayecto por cumplido—.
     */
    private void attemptComplete() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, R.string.driver_home_location_permission_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        LoadingButtonHelper.setLoading(btnTripAction, true);

        // La del flujo en vivo si la hay: ver liveLocation(). Aquí no es un detalle de pintura, es
        // la posición que el servidor compara contra el destino para dejar cerrar el viaje.
        LatLng enVivo = liveLocation();
        if (enVivo != null) {
            sendComplete(enVivo.latitude, enVivo.longitude);
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        LoadingButtonHelper.setLoading(btnTripAction, false);
                        Toast.makeText(this, R.string.driver_trip_complete_error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendComplete(location.getLatitude(), location.getLongitude());
                })
                .addOnFailureListener(e -> {
                    LoadingButtonHelper.setLoading(btnTripAction, false);
                    Toast.makeText(this, R.string.driver_trip_complete_error, Toast.LENGTH_SHORT).show();
                });
    }

    private void sendComplete(double lat, double lng) {
        completeInFlight = true;
        final LatLng cerradoEn = new LatLng(lat, lng);
        driverRepository.completeRide(rideId, lat, lng, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride result) {
                completeInFlight = false;
                LoadingButtonHelper.setLoading(btnTripAction, false);
                terminalStateHandled = true;
                currentStatus = "COMPLETED";
                stopLocationLoop();
                // Se suelta la cámara antes de colocarla: quitar las actualizaciones no cancela las
                // que ya iban camino del hilo principal, y una sola que llegue tarde con el
                // seguimiento encendido vuelve a apuntar la cámara por su cuenta.
                followingDriver = false;
                // El sitio del cierre queda fijado aquí: es el que acaba de viajar al servidor y el
                // que este validó contra el destino.
                tripEndLocation = cerradoEn;
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
                showProximityAwareError(error, ApiErrorCode.TOO_FAR_FROM_DROPOFF,
                        R.string.driver_trip_complete_error);
            }
        });
    }

    /**
     * Un error del servidor, dicho de la forma más útil que se pueda.
     *
     * <p>Cuando el motivo es la distancia, el mensaje del servidor trae los metros reales ("Estás a
     * 480 m del destino; acércate a menos de 250 m") y es exactamente lo que el conductor necesita
     * para saber qué hacer. Un aviso genérico ahí lo dejaría tocando el botón sin entender por qué
     * no pasa nada. Para cualquier otro fallo se usa el texto propio, que no depende de que el
     * servidor escriba en un idioma ni en un tono concretos.
     */
    private void showProximityAwareError(ApiException error, ApiErrorCode proximityCode,
                                          int fallbackRes) {
        String message = error.getCode() == proximityCode && error.getMessage() != null
                ? error.getMessage() : getString(fallbackRes);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @SuppressLint("MissingPermission")
    private void recenterMapOnDriver() {
        if (googleMap == null) {
            return;
        }
        // El viaje terminó: la vista de conducción deja de tener sentido y la cámara se endereza.
        // Sin esto el panel de cobro salía sobre un mapa todavía inclinado y girado al rumbo que
        // llevaba al llegar.
        setNavigationView(false);
        LatLng destino = cameraTarget();
        if (destino != null) {
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                    new CameraPosition.Builder()
                            .target(destino).zoom(SELF_ZOOM).tilt(0f).bearing(0f).build()));
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
                hasLiveFix = true;
                Double heading = location.hasBearing() ? (double) location.getBearing() : null;
                if (heading != null) {
                    lastBearing = heading;
                }
                if (routePainter.isReady()) {
                    routePainter.updateDriverPosition(lastKnownLocation, heading);
                }
                // La cámara lo acompaña mientras no haya arrastrado el mapa. Fuera de la vista de
                // navegación solo se mueve el centro: el acercamiento que él haya elegido es suyo.
                if (followingDriver && googleMap != null) {
                    if (navigationView && heading != null) {
                        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                                new CameraPosition.Builder()
                                        .target(lastKnownLocation)
                                        .zoom(googleMap.getCameraPosition().zoom)
                                        .tilt(googleMap.getCameraPosition().tilt)
                                        .bearing(heading.floatValue())
                                        .build()));
                    } else {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLng(lastKnownLocation));
                    }
                }
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
