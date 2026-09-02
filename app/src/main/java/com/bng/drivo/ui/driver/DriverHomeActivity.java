package com.bng.drivo.ui.driver;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bng.drivo.R;
import com.bng.drivo.data.model.DriverApplication;
import com.bng.drivo.data.model.InboxEntry;
import com.bng.drivo.data.model.IncomingRequest;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.model.Waypoint;
import com.bng.drivo.data.model.Wallet;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.ConnectivityRepository;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.DriverRideRealtimeRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.FirestoreDriverRideRealtimeRepository;
import com.bng.drivo.data.repository.RealtimeSubscription;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.SystemConnectivityRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import com.bng.drivo.ui.map.DriverRoutePainter;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.ui.map.MarkerIconFactory;
import com.bng.drivo.util.DrawerInsets;
import com.bng.drivo.util.LoadingButtonHelper;
import com.bng.drivo.util.PlaceTextResolver;
import com.bng.drivo.util.RideAlert;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Home del conductor sobre un único mapa, con el mismo esqueleto que {@code HomeFragment} del
 * pasajero: mapa a pantalla completa de larga vida, dos botones flotantes redondos arriba, radar
 * centrado en el hueco visible y un modal persistente abajo cuyo panel cambia según el estado.
 *
 * <p>Antes esta pantalla era una barra superior con una píldora descuadrada más tarjetas sueltas
 * flotando con sus propios márgenes: cada una adivinaba cuánto espacio le quedaba, y la solicitud
 * entrante aparecía como una tarjeta distinta en otro sitio de la pantalla. Ahora los estados son
 * pasos de un mismo modal ({@link Step}) y el mapa recibe el alto real de lo que le tapan, así que
 * el radar cae siempre centrado en lo que queda a la vista — ver {@link #updateSheetStops}.
 *
 * <p>Antes de mostrar nada gatea por GET /driver/application: solo un conductor "approved" ve el
 * modal; los demás estados (draft/pending_review/rejected/suspended) muestran una tarjeta centrada
 * y ni se infla el mapa ni se pide el permiso de ubicación (ver
 * {@link #setUpMapAndLocationIfNeeded()}).
 *
 * <p>C3 (oferta entrante) vive aquí mismo como un paso del modal, no como una Activity separada —
 * el contrato es explícito (openapi.yaml, POST /driver/rides/{id}/offer): "el conductor no se
 * bloquea, después de ofertar vuelve al radar y puede seguir recibiendo viajes".
 *
 * <p>Postularse por tanto <b>no ocupa el modal</b>: la oferta se va a un banner sobre el mapa
 * (ver {@link #bindOfferBanners}) y el modal vuelve al radar, libre para la siguiente solicitud.
 * Antes había un paso {@code OFFER_SENT} que se quedaba esperando una respuesta que puede tardar
 * los tres minutos de la subasta entera, y durante los cuales el conductor no podía hacer nada.
 *
 * <p>Ganar un viaje llega por dos caminos que se refuerzan: el push {@code offer_accepted} y
 * {@code GET /driver/current-ride}, que esta pantalla consulta al arrancar, al volver del fondo y
 * cada vez que una oferta desaparece de la bandeja. El push solo es un atajo — si no llega o el
 * conductor no lo toca, quedaba ocupado en el servidor y fuera del radar sin nada en pantalla
 * que se lo dijera.
 */
public class DriverHomeActivity extends AuthenticatedActivity implements OnMapReadyCallback {

    /** Estados del modal. GATE y NOT_APPROVED no lo usan: ahí el modal está oculto del todo. */
    private enum Step {
        /** Consultando GET /driver/application; solo se ve el spinner. */
        GATE,
        /** draft / pending_review / rejected / suspended: tarjeta centrada, sin mapa. */
        NOT_APPROVED,
        /** Aprobado y fuera del radar: saldo, viajes de hoy y botón de conectarse. */
        OFFLINE,
        /** En el radar: mismo panel con otro copy, más los anillos animados sobre el mapa. */
        ONLINE,
        /** Solicitud entrante con los datos del pasajero, contraofertas y ofertar/ignorar. */
        REQUEST
    }

    private static final long RADAR_PULSE_DURATION_MS = 1600L;
    /**
     * Radio base de los anillos del radar, en metros — no en dp. Al vivir como Circle sobre el
     * mapa (anclados a lastKnownLocation) en vez de como View en pantalla, su tamaño ya no es fijo:
     * escala con el zoom real de la cámara, igual que cualquier otro overlay del mapa. Estos
     * valores están calibrados para verse como los anillos antiguos (160dp/100dp con animación
     * 0.85x-1.15x) al zoom 16, el que usa animateCamera al centrar — a otros zooms se ven
     * proporcionalmente más grandes o chicos, que es el comportamiento esperado.
     */
    private static final double RADAR_OUTER_BASE_RADIUS_METERS = 180.0;
    private static final double RADAR_INNER_BASE_RADIUS_METERS = 112.5;
    /**
     * Cadencia con la que se refresca el coche en el mapa. Son los mismos 5 s que pedía la capa
     * "mi ubicación" del SDK de Maps mientras existía el punto azul: el coche lo reemplaza, así
     * que hereda su ritmo — con los 12 s de antes se veía dar saltos, no moverse.
     */
    private static final long LOCATION_INTERVAL_IDLE_MS = 5000L;
    /**
     * Cada cuánto se le manda la posición al servidor. Se mantiene en los 12 s de siempre aunque
     * el mapa se refresque más seguido: acelerar el dibujo no es razón para triplicar el tráfico
     * ni el consumo de datos del conductor — ver el filtro por tiempo en startLocationLoop().
     */
    private static final long LOCATION_REPORT_INTERVAL_MS = 12000L;
    private static final long BANNER_FADE_MS = 200;
    private static final long RECONNECTED_BANNER_VISIBLE_MS = 2500;
    /** Fundido del contenido del modal al cambiar de paso (mitad de salida, mitad de entrada). */
    private static final long PANEL_FADE_OUT_MS = 110;
    private static final long PANEL_FADE_IN_MS = 190;
    /** Igual que el layout_margin de btn_menu / btn_my_location en activity_driver_home.xml. */
    private static final int FLOATING_BUTTON_MARGIN_DP = 16;
    /** Alto de esos mismos flotantes: los banners se apilan justo debajo de su fila. */
    private static final int FLOATING_BUTTON_SIZE_DP = 44;
    /** Tope del corte del modal respecto a su propio alto — mismo criterio que en el pasajero. */
    private static final int SHEET_MIN_EXPAND_TRAVEL_DP = 56;
    /** A partir de aquí la cuenta atrás se pone en rojo. */
    private static final int EXPIRY_WARNING_SECONDS = 10;
    /**
     * Cuánto se queda en pantalla un banner ya resuelto antes de irse — y, cuando ganamos, antes
     * de que se abra el viaje. Es el tiempo de leer qué pasó.
     */
    private static final long BANNER_RESOLVED_VISIBLE_MS = 2000;
    /** Refresco de la cuenta atrás de los banners. */
    private static final long BANNER_TICK_MS = 500;

    private DriverRepository driverRepository;
    private UserRepository userRepository;
    private AuthRepository authRepository;
    private ConnectivityRepository connectivityRepository;
    private final DriverRideRealtimeRepository realtimeRepository = new FirestoreDriverRideRealtimeRepository();
    private FusedLocationProviderClient fusedLocationClient;

    private GoogleMap googleMap;
    private DriverRoutePainter routePainter;
    private boolean mapInitialized;
    /** Última posición conocida del conductor: origen del tramo de recogida en el mapa. */
    @Nullable
    private LatLng lastKnownLocation;
    private ValueAnimator radarAnimator;
    /** Anillos del radar anclados a lastKnownLocation — null hasta que el mapa está listo. */
    @Nullable
    private Circle radarRingOuter;
    @Nullable
    private Circle radarRingInner;
    /** El paso puede pedir visibilidad antes de que el mapa (y por tanto los Circle) exista. */
    private boolean radarVisibleRequested;
    /**
     * El coche del conductor mismo, visible desde que hay ubicación conocida — reemplaza al
     * punto azul de Maps (apagado en {@link #enableMyLocation()}) para que se vea igual que el
     * resto de la app. Se oculta solo durante {@link Step#REQUEST}: ahí el mismo coche ya lo
     * pinta {@link #routePainter} sobre el tramo de recogida, y tener los dos duplicaría el ícono.
     */
    @Nullable
    private Marker selfMarker;
    private RealtimeSubscription inboxSubscription;
    @Nullable
    private RealtimeSubscription connectivitySubscription;
    private LocationCallback locationCallback;
    /**
     * Cuándo se le habló al servidor por última vez ({@link SystemClock#elapsedRealtime()}, que no
     * salta si cambia la hora del sistema). Separa el ritmo del dibujo del ritmo del reporte.
     */
    private long lastLocationReportAtMs;
    private CountDownTimer incomingExpiryTimer;
    private String displayedRideId;
    /**
     * true si displayedRideId se abrió desde un banner de oferta ya enviada: el panel se pinta de
     * solo lectura (sin contraoferta ni botón de ofertar) porque el servidor ya no deja cambiar
     * esa oferta (ALREADY_OFFERED) — ver showIncomingRequest().
     */
    private boolean displayedRideReadOnly;
    /**
     * Viajes sobre los que este conductor ya decidió (ignorados u ofertados) y que siguen en su
     * bandeja porque otros conductores todavía están ofertando. Es un conjunto y no un solo id
     * porque puede haber varios a la vez: con una oferta en vuelo el conductor sigue recibiendo
     * solicitudes, y puede ignorar dos o tres antes de que la primera se resuelva. Las entradas
     * se borran cuando el viaje desaparece de la bandeja (ver {@link #onInboxChanged}).
     */
    private final Set<String> ignoredRideIds = new HashSet<>();
    /** Banners en pantalla, por id de viaje: las ofertas vivas de este conductor. */
    private final Map<String, OfferBanner> offerBanners = new LinkedHashMap<>();
    /** Viajes cuyo banner ya se resolvió (ganado o perdido) y está en su animación de salida. */
    private final Set<String> closingBanners = new HashSet<>();
    /** Evita que dos consultas a /driver/current-ride se pisen al resolverse varias ofertas. */
    private boolean checkingCurrentRide;
    /** Ya estamos abriendo el viaje ganado: ni volver a abrirlo ni seguir pintando banners. */
    private boolean openingWonRide;

    private boolean online;
    private boolean approved;
    private Step step = Step.GATE;
    /** Paso realmente puesto en el modal: va un fundido por detrás de {@link #step} al animar. */
    private Step displayedStep = Step.GATE;

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private View navHeader;
    private int navHeaderBasePaddingPx;
    private View btnMenu;
    private View btnMyLocation;
    private View cardNotApproved;
    private ImageView iconNotApproved;
    private TextView textNotApprovedTitle;
    private TextView textNotApprovedDetail;
    private MaterialButton btnNotApprovedAction;
    private View progressGate;

    private View sheetContainer;
    private View sheetContent;
    private BottomSheetBehavior<View> sheetBehavior;
    private View panelConnect;
    private View panelRequest;
    private LinearLayout containerOfferBanners;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable bannerTicker;
    private int lastCollapsedHeightPx = -1;
    private int sheetTopInsetPx;
    private int sheetAvailableHeightPx;
    private int lastMapBottomPaddingPx = -1;
    private int lastMapTopPaddingPx = -1;
    private boolean animateNextPeek;

    private TextView textConnectGreeting;
    private View dotConnectionStatus;
    private TextView textConnectionStatus;
    private TextView textConnectionHint;
    private MaterialButton btnConnectToggle;

    private TextView textNavAvatar;
    private TextView textNavName;
    private TextView textNavBalance;
    private TextView textNavTripsToday;

    private TextView textIncomingTitle;
    private TextView textIncomingAvatar;
    private TextView textIncomingName;
    private TextView textIncomingRating;
    private TextView textIncomingOffer;
    private TextView textIncomingPickupText;
    private TextView textIncomingPickupDistance;
    private LinearLayout containerIncomingStops;
    private TextView textIncomingDropoffText;
    private TextView textIncomingTripDistance;
    private TextView textIncomingExpiry;
    private TextView textIncomingCounterLabel;
    private LinearLayout containerIncomingCounters;
    private MaterialButton btnIncomingIgnore;
    private MaterialButton btnIncomingAccept;
    private ProgressBar progressIncomingExpiry;


    private View offlineBanner;
    private View onlineBanner;
    private final Handler reconnectedBannerHandler = new Handler(Looper.getMainLooper());
    private boolean wasOffline;

    private OnBackPressedCallback backCallback;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                if (hasLocationPermission() && googleMap != null) {
                    enableMyLocation();
                    startLocationLoop();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_home);

        driverRepository = new RestDriverRepository(this);
        userRepository = new RestUserRepository(this);
        authRepository = new FirebaseAuthRepository();
        connectivityRepository = new SystemConnectivityRepository(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        routePainter = new DriverRoutePainter(this);

        bindViews();
        // setUpBackHandling primero: setUpDrawer engancha su listener al backCallback.
        setUpBackHandling();
        setUpDrawer();
        setUpBottomSheet();

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnMyLocation.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                enableMyLocation();
            } else {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
        btnConnectToggle.setOnClickListener(v -> toggleConnection());
        // btnIncomingIgnore no tiene listener fijo: showIncomingRequest() se lo pone en cada
        // apertura, distinto según sea una solicitud nueva o una oferta ya enviada (solo lectura).

        startRadarPulse();
        loadGreeting();
        runApplicationGate();
    }

    private void bindViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        btnMenu = findViewById(R.id.btn_menu);
        btnMyLocation = findViewById(R.id.btn_my_location);
        cardNotApproved = findViewById(R.id.card_not_approved);
        iconNotApproved = findViewById(R.id.icon_not_approved);
        textNotApprovedTitle = findViewById(R.id.text_not_approved_title);
        textNotApprovedDetail = findViewById(R.id.text_not_approved_detail);
        btnNotApprovedAction = findViewById(R.id.btn_not_approved_action);
        progressGate = findViewById(R.id.progress_gate);
        offlineBanner = findViewById(R.id.banner_offline);
        onlineBanner = findViewById(R.id.banner_online);

        sheetContainer = findViewById(R.id.sheet_container);
        sheetContent = findViewById(R.id.sheet_content);
        panelConnect = findViewById(R.id.panel_driver_connect);
        panelRequest = findViewById(R.id.panel_driver_request);
        containerOfferBanners = findViewById(R.id.container_offer_banners);

        textConnectGreeting = findViewById(R.id.text_connect_greeting);
        dotConnectionStatus = findViewById(R.id.dot_connection_status);
        textConnectionStatus = findViewById(R.id.text_connection_status);
        textConnectionHint = findViewById(R.id.text_connection_hint);
        btnConnectToggle = findViewById(R.id.btn_connect_toggle);

        textIncomingTitle = findViewById(R.id.text_incoming_title);
        textIncomingAvatar = findViewById(R.id.text_incoming_avatar);
        textIncomingName = findViewById(R.id.text_incoming_name);
        textIncomingRating = findViewById(R.id.text_incoming_rating);
        textIncomingOffer = findViewById(R.id.text_incoming_offer);
        textIncomingPickupText = findViewById(R.id.text_incoming_pickup_text);
        textIncomingPickupDistance = findViewById(R.id.text_incoming_pickup_distance);
        containerIncomingStops = findViewById(R.id.container_incoming_stops);
        textIncomingDropoffText = findViewById(R.id.text_incoming_dropoff_text);
        textIncomingTripDistance = findViewById(R.id.text_incoming_trip_distance);
        textIncomingExpiry = findViewById(R.id.text_incoming_expiry);
        textIncomingCounterLabel = findViewById(R.id.text_incoming_counter_label);
        containerIncomingCounters = findViewById(R.id.container_incoming_counters);
        btnIncomingIgnore = findViewById(R.id.btn_incoming_ignore);
        btnIncomingAccept = findViewById(R.id.btn_incoming_accept);
        progressIncomingExpiry = findViewById(R.id.progress_incoming_expiry);

    }

    /**
     * Menú general de la app, no de esta pantalla: por eso es un cajón lateral y no un modal,
     * igual que en el lado del pasajero. La diferencia es que aquí las otras tres secciones son
     * Activities propias, así que solo "Inicio" queda marcado — las demás abren su pantalla y al
     * volver seguimos en Inicio.
     */
    private void setUpDrawer() {
        navHeader = navView.getHeaderView(0);
        navHeaderBasePaddingPx = navHeader.getPaddingTop();
        textNavAvatar = navHeader.findViewById(R.id.text_nav_avatar);
        textNavName = navHeader.findViewById(R.id.text_nav_name);
        textNavBalance = navHeader.findViewById(R.id.text_nav_balance);
        textNavTripsToday = navHeader.findViewById(R.id.text_nav_trips_today);
        // La cabecera resume el dinero; el detalle real (libro contable) vive en Ganancias.
        navHeader.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            startActivity(new Intent(this, DriverEarningsActivity.class));
        });

        navView.setCheckedItem(R.id.nav_driver_inicio);
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);
            if (id == R.id.nav_driver_inicio) {
                return true;
            }
            if (id == R.id.nav_driver_earnings) {
                startActivity(new Intent(this, DriverEarningsActivity.class));
            } else if (id == R.id.nav_driver_settings) {
                startActivity(new Intent(this, DriverSettingsActivity.class));
            } else if (id == R.id.nav_driver_security) {
                startActivity(new Intent(this, DriverSecurityActivity.class));
            }
            // false a propósito: NavigationView solo marca el item cuando el listener devuelve
            // true, y estas tres opciones abren una Activity aparte — no son "dónde estás". Con
            // true se quedaban resaltadas al volver con el botón atrás, señalando una sección en
            // la que ya no estabas. Inicio, que sí vive en esta Activity, sigue devolviendo true.
            return false;
        });
        // DrawerLayout no cierra solo con "atrás": sin esto, estando el cajón abierto el gesto
        // saldría de la app en vez de cerrarlo.
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                // NavigationView deja de compensar la status bar en cuanto hay cabecera; sin esto
                // el nombre quedaba pegado al reloj — ver DrawerInsets.
                DrawerInsets.applyTopInset(navHeader, navHeaderBasePaddingPx);
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                backCallback.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                backCallback.setEnabled(step == Step.REQUEST);
            }
        });
    }

    /**
     * Bloquea el cajón cuando no hay nada que abrir (gate) o cuando hay una solicitud en pantalla
     * con su cuenta atrás corriendo. No basta con esconder el botón: el cajón también se abre
     * deslizando desde el borde, y ese gesto sacaría al conductor de la decisión — mismo criterio
     * que HomeActivity.setDrawerEnabled() del pasajero.
     */
    private void setDrawerEnabled(boolean enabled) {
        drawerLayout.setDrawerLockMode(enabled
                ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    // ---------------------------------------------------------------------------------------
    // Pasos del flujo
    // ---------------------------------------------------------------------------------------

    private void goTo(Step next) {
        if (step == next) {
            return;
        }
        step = next;
        applyStep(next, true);
    }

    /**
     * Único punto donde el estado se traduce a UI: qué panel se ve, qué tapa el mapa, qué
     * flotantes hay arriba y qué hace el botón de atrás. Mismo papel que
     * {@code HomeFragment.applyStep()} en el lado del pasajero.
     */
    private void applyStep(Step target, boolean animate) {
        boolean gated = target == Step.GATE || target == Step.NOT_APPROVED;

        progressGate.setVisibility(target == Step.GATE ? View.VISIBLE : View.GONE);
        cardNotApproved.setVisibility(target == Step.NOT_APPROVED ? View.VISIBLE : View.GONE);
        sheetContainer.setVisibility(gated ? View.GONE : View.VISIBLE);
        btnMenu.setVisibility(gated ? View.GONE : View.VISIBLE);
        // Sin mapa detrás no hay nada que recentrar; con la solicitud en pantalla, tampoco: el
        // foco es decidir, y el mapa está congelado bajo el modal.
        btnMyLocation.setVisibility(gated || target == Step.REQUEST ? View.GONE : View.VISIBLE);
        // El radar late mientras estamos en el radar de verdad — también con una oferta ya
        // enviada, porque el conductor sigue recibiendo viajes (ver el javadoc de la clase).
        setRadarVisible(target == Step.ONLINE);
        updateSelfMarkerVisibility();
        setDrawerEnabled(!gated && target != Step.REQUEST);
        // Las dos rutas solo tienen sentido con una solicitud en pantalla; en cuanto se resuelve
        // (ofertada o ignorada) el mapa vuelve a estar limpio para la siguiente.
        if (target != Step.REQUEST && routePainter.isReady()) {
            routePainter.clear();
        }

        if (!gated) {
            swapPanel(target, animate);
        } else {
            displayedStep = target;
        }

        // "Atrás" solo tiene sentido cuando hay algo que cerrar dentro del modal; en OFFLINE y
        // ONLINE debe salir de la app como siempre.
        backCallback.setEnabled(target == Step.REQUEST);
        updateSheetStops();
    }

    /**
     * Fundido del contenido del modal: se apaga, se cambia el panel visible y se vuelve a
     * encender. Se funde el contenedor entero y no un panel contra otro a propósito — con los dos
     * visibles a la vez, aunque fueran 100 ms, el modal mediría el alto del más grande y daría un
     * tirón antes de asentarse (mismo motivo que en HomeFragment.swapPanel).
     */
    private void swapPanel(Step target, boolean animate) {
        Runnable swap = () -> {
            panelConnect.setVisibility(
                    target == Step.OFFLINE || target == Step.ONLINE ? View.VISIBLE : View.GONE);
            panelRequest.setVisibility(target == Step.REQUEST ? View.VISIBLE : View.GONE);
            displayedStep = target;
            lastCollapsedHeightPx = -1;
            animateNextPeek = animate;
        };
        // Entre OFFLINE y ONLINE no cambia el panel, solo su copy: fundir ahí haría parpadear
        // una pantalla que en realidad no cambió de sitio.
        boolean samePanel = (displayedStep == Step.OFFLINE && target == Step.ONLINE)
                || (displayedStep == Step.ONLINE && target == Step.OFFLINE);
        if (!animate || samePanel) {
            swap.run();
            sheetContent.setAlpha(1f);
            return;
        }
        sheetContent.animate().cancel();
        sheetContent.animate().alpha(0f).setDuration(PANEL_FADE_OUT_MS).withEndAction(() -> {
            swap.run();
            sheetContent.animate().alpha(1f).setDuration(PANEL_FADE_IN_MS).start();
        }).start();
    }

    private void setUpBackHandling() {
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                if (step == Step.REQUEST) {
                    ignoreIncomingRequest();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    // ---------------------------------------------------------------------------------------
    // Gate de aprobación
    // ---------------------------------------------------------------------------------------

    private void runApplicationGate() {
        step = Step.GATE;
        applyStep(Step.GATE, false);
        driverRepository.getApplication(new ApiCallback<DriverApplication>() {
            @Override
            public void onSuccess(DriverApplication application) {
                if ("approved".equals(application.getStatus())) {
                    approved = true;
                    step = online ? Step.ONLINE : Step.OFFLINE;
                    applyStep(step, false);
                    updateConnectionUi();
                    setUpMapAndLocationIfNeeded();
                    loadWallet();
                } else {
                    approved = false;
                    showNotApprovedState(application);
                }
            }

            @Override
            public void onError(ApiException error) {
                approved = false;
                if (error.getCode() == ApiErrorCode.NO_APPLICATION) {
                    startActivity(new Intent(DriverHomeActivity.this, DriverRegistrationActivity.class));
                    finish();
                    return;
                }
                step = Step.NOT_APPROVED;
                applyStep(Step.NOT_APPROVED, false);
                iconNotApproved.setImageResource(R.drawable.ic_close);
                textNotApprovedTitle.setText(R.string.driver_home_status_title_error);
                textNotApprovedDetail.setText(R.string.driver_home_status_detail_error);
                showNotApprovedAction(R.string.driver_home_status_action_retry, v -> runApplicationGate());
            }
        });
    }

    /** Sin acceso a Ganancias/Configuración/Seguridad todavía — esas pantallas asumen un
     * conductor ya operando, y aquí solo hay una solicitud en algún estado no aprobado. */
    private void showNotApprovedState(DriverApplication application) {
        step = Step.NOT_APPROVED;
        applyStep(Step.NOT_APPROVED, false);
        String status = application.getStatus();
        if ("draft".equals(status)) {
            iconNotApproved.setImageResource(R.drawable.ic_edit);
            textNotApprovedTitle.setText(R.string.driver_home_status_title_draft);
            textNotApprovedDetail.setText(R.string.driver_home_status_detail_draft);
            showNotApprovedAction(R.string.driver_home_status_action_continue_registration, v ->
                    startActivity(new Intent(this, DriverRegistrationActivity.class)));
        } else if ("rejected".equals(status)) {
            iconNotApproved.setImageResource(R.drawable.ic_close);
            textNotApprovedTitle.setText(R.string.driver_home_status_title_rejected);
            String reason = application.getRejectionReason();
            if (reason != null && !reason.isEmpty()) {
                textNotApprovedDetail.setText(getString(R.string.driver_home_status_detail_rejected_reason, reason));
            } else {
                textNotApprovedDetail.setText(R.string.driver_home_status_detail_rejected_generic);
            }
            btnNotApprovedAction.setVisibility(View.GONE);
        } else if ("suspended".equals(status)) {
            iconNotApproved.setImageResource(R.drawable.ic_close);
            textNotApprovedTitle.setText(R.string.driver_home_status_title_suspended);
            textNotApprovedDetail.setText(R.string.driver_home_status_detail_suspended);
            btnNotApprovedAction.setVisibility(View.GONE);
        } else {
            iconNotApproved.setImageResource(R.drawable.ic_clock);
            textNotApprovedTitle.setText(R.string.driver_home_status_title_pending_review);
            textNotApprovedDetail.setText(R.string.driver_home_status_detail_pending_review);
            showNotApprovedAction(R.string.driver_home_status_action_refresh, v -> runApplicationGate());
        }
    }

    /** El estado no aprobado ya no se refresca tocando la tarjeta (no había ninguna pista
     * visual de que fuera tocable): cada estado expone su propia acción explícita. */
    private void showNotApprovedAction(int labelRes, View.OnClickListener listener) {
        btnNotApprovedAction.setVisibility(View.VISIBLE);
        btnNotApprovedAction.setText(labelRes);
        btnNotApprovedAction.setOnClickListener(listener);
    }

    // ---------------------------------------------------------------------------------------
    // Conectarse / desconectarse
    // ---------------------------------------------------------------------------------------

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
                goTo(Step.ONLINE);
                updateConnectionUi();
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
                displayedRideId = null;
                ignoredRideIds.clear();
                // Al desconectarse el servidor deja de ofrecerle viajes, pero sus ofertas siguen
                // vivas hasta que vencen o alguien las elige. Los banners se van de la pantalla
                // porque ya no hay bandeja que los mantenga al día; si un pasajero lo elige,
                // /driver/current-ride lo trae de vuelta al volver.
                clearOfferBanners();
                cancelIncomingExpiryTimer();
                goTo(Step.OFFLINE);
                updateConnectionUi();
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

    /** Copy y color del panel de conexión. El punto de color es lo que se lee de un vistazo. */
    private void updateConnectionUi() {
        if (online) {
            btnConnectToggle.setText(R.string.driver_home_disconnect_button);
            textConnectionStatus.setText(R.string.driver_home_status_online);
            textConnectionStatus.setTextColor(getColor(R.color.drivo_success));
            dotConnectionStatus.setBackgroundTintList(getColorStateList(R.color.drivo_success));
            textConnectionHint.setText(R.string.driver_home_online_subtitle);
        } else {
            btnConnectToggle.setText(R.string.driver_home_connect_button);
            textConnectionStatus.setText(R.string.driver_home_status_offline);
            textConnectionStatus.setTextColor(getColor(R.color.drivo_on_background));
            dotConnectionStatus.setBackgroundTintList(getColorStateList(R.color.drivo_error));
            textConnectionHint.setText(R.string.driver_home_offline_subtitle);
        }
    }

    private void loadGreeting() {
        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                String firstName = profile.getName() != null ? profile.getName().split("\\s+")[0] : "";
                textConnectGreeting.setText(getString(R.string.driver_home_greeting, firstName));
                textNavAvatar.setText(profile.getInitials());
                textNavName.setText(profile.getName());
            }

            @Override
            public void onError(ApiException error) {
                // El saludo es cosmético; sin nombre se queda el placeholder del layout.
            }
        });
    }

    /**
     * Saldo y viajes de hoy para la cabecera del drawer. El wallet es el único origen real de
     * dinero del contrato: los viajes de hoy se cuentan por sus filas {@code commission} (una por
     * viaje cerrado), el mismo criterio que DriverEarningsActivity para que las dos pantallas no
     * se contradigan.
     */
    private void loadWallet() {
        driverRepository.getWallet(new ApiCallback<Wallet>() {
            @Override
            public void onSuccess(Wallet wallet) {
                textNavBalance.setText(String.format(Locale.getDefault(),
                        getString(R.string.driver_home_wallet_balance_format), wallet.getBalance()));
                textNavTripsToday.setText(getString(R.string.driver_home_trips_today_value,
                        countTripsToday(wallet)));
            }

            @Override
            public void onError(ApiException error) {
                textNavBalance.setText(R.string.driver_home_wallet_unavailable);
                textNavTripsToday.setText(R.string.driver_settings_stat_empty);
            }
        });
    }

    private int countTripsToday(Wallet wallet) {
        LocalDate today = LocalDate.now();
        int trips = 0;
        for (Wallet.WalletEntry entry : wallet.getEntries()) {
            if (!"commission".equals(entry.getType())) {
                continue;
            }
            Long millis = parseInstantMillis(entry.getCreatedAt());
            if (millis != null && Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                    .toLocalDate().equals(today)) {
                trips++;
            }
        }
        return trips;
    }

    // ---------------------------------------------------------------------------------------
    // Solicitudes entrantes
    // ---------------------------------------------------------------------------------------

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

    /**
     * La bandeja es la única señal en vivo del lado del conductor: un documento aparece cuando un
     * viaje entra a su radar y desaparece cuando sale (lo tomó otro, lo ganó él, o venció).
     *
     * <p>Cada documento dice además si este conductor ya se postuló ({@code my_offer}), y de ahí
     * sale el reparto: lo ofertado va a los banners sobre el mapa, y lo que queda por decidir al
     * modal. Que ese estado venga del servidor y no de la memoria del proceso es lo que hace que
     * los banners sobrevivan a que Android recicle la app con las ofertas todavía vivas.
     *
     * <p>Que una oferta desaparezca de la bandeja no dice si ganamos o perdimos — el canal en vivo
     * no lo distingue —, así que se le pregunta al servidor: {@link #checkCurrentRide}.
     */
    private void onInboxChanged(List<InboxEntry> entries) {
        if (openingWonRide) {
            return;
        }
        List<String> rideIds = new ArrayList<>();
        for (InboxEntry entry : entries) {
            rideIds.add(entry.getRideId());
        }
        ignoredRideIds.retainAll(rideIds);

        boolean seResolvioAlguna = bindOfferBanners(entries);
        if (seResolvioAlguna) {
            // Una de nuestras ofertas se decidió. Solo el servidor sabe si a nuestro favor.
            checkCurrentRide(false);
        }

        if (displayedRideId != null && !rideIds.contains(displayedRideId)) {
            // Se lo llevó otro conductor o venció mientras lo teníamos en pantalla.
            displayedRideId = null;
            if (step == Step.REQUEST) {
                backToRadar();
            }
        }
        // Solo se abre una solicitud si el modal está libre: con el conductor leyendo una,
        // sustituírsela por otra que acaba de entrar le hace decidir sobre algo que no eligió
        // ver — y la que estaba leyendo se pierde sin que sepa que existió.
        if (step == Step.REQUEST || step == Step.GATE || step == Step.NOT_APPROVED) {
            return;
        }
        for (InboxEntry entry : entries) {
            if (!entry.hasOffered() && !ignoredRideIds.contains(entry.getRideId())) {
                fetchIncomingRequest(entry.getRideId());
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Banners de ofertas enviadas
    // ---------------------------------------------------------------------------------------

    /** Un banner vivo y las vistas que hay que refrescar en cada tic. */
    private static final class OfferBanner {
        final View view;
        final TextView countdown;
        final ProgressBar progress;
        @Nullable
        final Long expiresAtMillis;
        final long totalMs;

        OfferBanner(View view, TextView countdown, ProgressBar progress,
                    @Nullable Long expiresAtMillis, long totalMs) {
            this.view = view;
            this.countdown = countdown;
            this.progress = progress;
            this.expiresAtMillis = expiresAtMillis;
            this.totalMs = totalMs;
        }
    }

    /**
     * Sincroniza los banners con la bandeja.
     *
     * @return true si alguna oferta que teníamos en pantalla ya no está — lo que obliga a
     *         preguntarle al servidor si el desenlace fue a nuestro favor
     */
    private boolean bindOfferBanners(List<InboxEntry> entries) {
        Set<String> vivos = new HashSet<>();
        for (InboxEntry entry : entries) {
            if (!entry.hasOffered()) {
                continue;
            }
            vivos.add(entry.getRideId());
            if (!offerBanners.containsKey(entry.getRideId())) {
                addOfferBanner(entry);
            }
        }

        boolean seResolvioAlguna = false;
        for (String rideId : new ArrayList<>(offerBanners.keySet())) {
            if (!vivos.contains(rideId) && !closingBanners.contains(rideId)) {
                seResolvioAlguna = true;
                closingBanners.add(rideId);
            }
        }
        startBannerTickerIfNeeded();
        return seResolvioAlguna;
    }

    private void addOfferBanner(InboxEntry entry) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_offer_banner, containerOfferBanners, false);
        ((TextView) card.findViewById(R.id.text_banner_destination))
                .setText(destinationTextOf(entry));
        ((TextView) card.findViewById(R.id.text_banner_state)).setText(
                getString(R.string.driver_banner_offer_sent_format,
                        String.format(Locale.getDefault(), "$%.2f", entry.getMyOffer())));

        Long expiresAt = entry.getExpiresAtMillis();
        long totalMs = expiresAt == null ? 0 : Math.max(0, expiresAt - System.currentTimeMillis());
        ProgressBar progress = card.findViewById(R.id.progress_banner_expiry);
        TextView countdown = card.findViewById(R.id.text_banner_countdown);
        if (expiresAt == null) {
            progress.setVisibility(View.GONE);
            countdown.setVisibility(View.GONE);
        }

        String rideId = entry.getRideId();
        double myOffer = entry.getMyOffer();
        // Volver a abrir la solicitud es de lectura: la oferta ya no se puede cambiar (el
        // servidor responde ALREADY_OFFERED), pero querer repasar a dónde iba es legítimo.
        card.setOnClickListener(v -> {
            if (step != Step.REQUEST) {
                fetchIncomingRequest(rideId, myOffer);
            }
        });

        containerOfferBanners.addView(card);
        offerBanners.put(rideId, new OfferBanner(card, countdown, progress, expiresAt, totalMs));
    }

    private String destinationTextOf(InboxEntry entry) {
        String dropoff = entry.getDropoffText();
        return dropoff == null || dropoff.trim().isEmpty()
                ? getString(R.string.driver_banner_unknown_destination) : dropoff;
    }

    /**
     * Cierra un banner diciendo qué pasó antes de irse. Si desapareciera en silencio se leería
     * como un fallo de la app, y es justo el momento en el que el conductor está pendiente.
     */
    private void closeOfferBanner(String rideId, boolean won) {
        OfferBanner banner = offerBanners.remove(rideId);
        closingBanners.remove(rideId);
        if (banner == null) {
            return;
        }
        TextView state = banner.view.findViewById(R.id.text_banner_state);
        state.setText(won ? R.string.driver_banner_won : R.string.driver_banner_gone);
        state.setTextColor(ContextCompat.getColor(this,
                won ? R.color.drivo_success : R.color.drivo_error));
        banner.view.findViewById(R.id.dot_banner_state).setBackgroundTintList(
                ContextCompat.getColorStateList(this,
                        won ? R.color.drivo_success : R.color.drivo_error));
        banner.progress.setVisibility(View.GONE);
        banner.countdown.setVisibility(View.GONE);

        banner.view.animate().alpha(0f).setStartDelay(BANNER_RESOLVED_VISIBLE_MS)
                .setDuration(BANNER_FADE_MS)
                .withEndAction(() -> containerOfferBanners.removeView(banner.view))
                .start();
    }

    private void clearOfferBanners() {
        offerBanners.clear();
        closingBanners.clear();
        containerOfferBanners.removeAllViews();
        stopBannerTicker();
    }

    /**
     * Un solo temporizador para todos los banners, no uno por tarjeta: todos corren contra el
     * mismo reloj —el de la subasta— y con N sueltos era cuestión de tiempo que alguno
     * sobreviviera a su banner.
     */
    private void startBannerTickerIfNeeded() {
        if (offerBanners.isEmpty()) {
            stopBannerTicker();
            return;
        }
        if (bannerTicker != null) {
            return;
        }
        // Un Handler y no un CountDownTimer: éste no cuenta hacia un final —vive mientras haya
        // banners— y CountDownTimer(Long.MAX_VALUE, …) desborda al sumarle el reloj del sistema
        // y termina en el acto.
        bannerTicker = new Runnable() {
            @Override
            public void run() {
                tickBanners();
                bannerHandler.postDelayed(this, BANNER_TICK_MS);
            }
        };
        bannerHandler.post(bannerTicker);
    }

    private void stopBannerTicker() {
        bannerHandler.removeCallbacksAndMessages(null);
        bannerTicker = null;
    }

    private void tickBanners() {
        long now = System.currentTimeMillis();
        for (OfferBanner banner : offerBanners.values()) {
            if (banner.expiresAtMillis == null || banner.totalMs <= 0) {
                continue;
            }
            long remaining = Math.max(0, banner.expiresAtMillis - now);
            banner.progress.setProgress((int) (1000 * remaining / banner.totalMs));
            long seconds = remaining / 1000;
            banner.countdown.setText(String.format(Locale.getDefault(), "%d:%02d",
                    seconds / 60, seconds % 60));
        }
    }

    /**
     * Pregunta al servidor si este conductor trae un viaje asignado.
     *
     * <p>Es el único camino fiable: el push {@code offer_accepted} puede no llegar, llegar tarde o
     * quedarse sin tocar, y quedar asignado sin saberlo lo saca del radar —el servidor no ofrece
     * viajes a quien ya tiene uno— sin nada en pantalla que lo explique. Se consulta al arrancar,
     * al volver del fondo y cada vez que una oferta se resuelve.
     *
     * @param silencioso true en el arranque: si no hay viaje no hay banner que cerrar
     */
    private void checkCurrentRide(boolean silencioso) {
        if (checkingCurrentRide || openingWonRide) {
            return;
        }
        checkingCurrentRide = true;
        driverRepository.getCurrentRide(new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                checkingCurrentRide = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (ride != null && ride.getId() != null) {
                    openWonRide(ride.getId());
                    return;
                }
                if (!silencioso) {
                    // Ninguna de las que se resolvieron era nuestra.
                    for (String rideId : new ArrayList<>(closingBanners)) {
                        closeOfferBanner(rideId, false);
                    }
                    startBannerTickerIfNeeded();
                }
            }

            @Override
            public void onError(ApiException error) {
                checkingCurrentRide = false;
                // Se reintenta al siguiente cambio de la bandeja o al volver del fondo. No se
                // cierran los banners: sin respuesta no sabemos qué pasó, y dar por perdida una
                // oferta que ganamos es el peor de los dos errores.
            }
        });
    }

    /**
     * Nos eligieron: el banner lo dice, suena, y el viaje se abre solo.
     *
     * <p>La pausa antes de cambiar de pantalla no es decorativa. El conductor puede estar
     * manejando y mirando el mapa; saltar de golpe a otra pantalla no le explica qué pasó, y este
     * es justo el momento en que necesita entenderlo.
     */
    private void openWonRide(String rideId) {
        if (openingWonRide) {
            return;
        }
        openingWonRide = true;
        cancelIncomingExpiryTimer();
        RideAlert.play(this);
        for (String otro : new ArrayList<>(offerBanners.keySet())) {
            // Las demás ofertas las acaba de cancelar el servidor al asignarnos ésta.
            closeOfferBanner(otro, otro.equals(rideId));
        }
        stopBannerTicker();

        Intent intent = new Intent(this, DriverActiveTripActivity.class);
        intent.putExtra(DriverActiveTripActivity.EXTRA_RIDE_ID, rideId);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                startActivity(intent);
            }
            openingWonRide = false;
        }, BANNER_RESOLVED_VISIBLE_MS);
    }

    private void fetchIncomingRequest(String rideId) {
        fetchIncomingRequest(rideId, null);
    }

    /**
     * @param myOfferedAmount no nulo cuando se abre desde un banner de oferta ya enviada: pinta
     *                        el panel de solo lectura con ese monto en vez del de una solicitud
     *                        nueva — ver showIncomingRequest().
     */
    private void fetchIncomingRequest(String rideId, @Nullable Double myOfferedAmount) {
        driverRepository.getIncomingRequest(rideId, new ApiCallback<IncomingRequest>() {
            @Override
            public void onSuccess(IncomingRequest request) {
                showIncomingRequest(request, myOfferedAmount);
            }

            @Override
            public void onError(ApiException error) {
                // Ya no existe o ya no es para nosotros (venció, lo tomaron) — sin pantalla de
                // error, el radar sigue como si nada.
            }
        });
    }

    private void showIncomingRequest(IncomingRequest request, @Nullable Double myOfferedAmount) {
        displayedRideId = request.getRideId();
        boolean readOnly = myOfferedAmount != null;
        displayedRideReadOnly = readOnly;
        textIncomingTitle.setText(readOnly
                ? R.string.incoming_request_title_offered : R.string.incoming_request_title);

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

        // De solo lectura muestra lo que este conductor ya ofertó (puede ser una contraoferta,
        // distinta de request.getOffer(), que es el precio del pasajero); si no, el precio a
        // ofertar es ese mismo precio del pasajero.
        textIncomingOffer.setText(String.format(Locale.getDefault(), "$ %.2f",
                readOnly ? myOfferedAmount : request.getOffer()));
        textIncomingDropoffText.setText(request.getDropoffText());
        // El pasajero manda su origen como "Tu ubicación actual" — desde aquí eso no dice nada, o
        // peor, se lee como la ubicación del conductor. Ver PlaceTextResolver.
        String rideId = request.getRideId();
        PlaceTextResolver.resolve(this, request.getPickupText(), latLngOf(request.getPickupLat(),
                request.getPickupLng()), text -> {
            // Puede volver tarde: si para entonces ya cambió la solicitud en pantalla, se ignora.
            if (rideId.equals(displayedRideId)) {
                textIncomingPickupText.setText(text);
            }
        });

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

        bindStops(request);
        bindCounterOffers(request, readOnly);

        // Ofertar/contraofertar ya no aplica: el servidor no deja cambiar una oferta ya enviada
        // (ALREADY_OFFERED). "Ignorar" tampoco tiene sentido — no es una solicitud nueva que
        // descartar, solo se está repasando una ya enviada — así que se convierte en "Cerrar".
        btnIncomingAccept.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        if (readOnly) {
            btnIncomingIgnore.setText(R.string.incoming_request_close_button);
            btnIncomingIgnore.setOnClickListener(v -> closeDisplayedRequest());
        } else {
            String offerText = String.format(Locale.getDefault(), "$%.2f", request.getOffer());
            btnIncomingAccept.setText(getString(R.string.incoming_request_accept_button_format, offerText));
            btnIncomingAccept.setOnClickListener(v ->
                    submitOffer(request.getRideId(), request.getOffer(), request.getPassengerName(), true));
            btnIncomingIgnore.setText(R.string.incoming_request_ignore_button);
            btnIncomingIgnore.setOnClickListener(v -> ignoreIncomingRequest());
        }

        goTo(Step.REQUEST);
        drawRequestRoutes(request);
        startIncomingExpiryCountdown(request.getExpiresAt());
        if (!readOnly) {
            // Con la app abierta el push no suena (el sistema no pinta la notificación), y un
            // conductor manejando no está viendo la pantalla — ver RideAlert. Al repasar una
            // oferta ya enviada no hay nada nuevo que avisar.
            RideAlert.play(this);
        }
    }

    /**
     * Paradas intermedias del pasajero, en orden y entre origen y destino. Cada una se resuelve
     * igual que el origen: si el servidor no manda texto (el contrato solo garantiza lat/lng para
     * los waypoints), se geocodifica la coordenada en vez de dejar la fila vacía.
     */
    private void bindStops(IncomingRequest request) {
        containerIncomingStops.removeAllViews();
        List<Waypoint> stops = request.getStops();
        containerIncomingStops.setVisibility(stops.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        String rideId = request.getRideId();
        for (Waypoint stop : stops) {
            View row = inflater.inflate(R.layout.item_incoming_stop, containerIncomingStops, false);
            TextView address = row.findViewById(R.id.text_stop_address);
            PlaceTextResolver.resolve(this, stop.getText(), new LatLng(stop.getLat(), stop.getLng()),
                    text -> {
                        if (rideId.equals(displayedRideId)) {
                            address.setText(text);
                        }
                    });
            containerIncomingStops.addView(row);
        }
    }

    /**
     * Los dos tramos del viaje sobre el mapa: azul del conductor al pasajero, verde del origen al
     * destino del pasajero. Son las dos cosas que hay que ver para decidir si conviene el viaje —
     * antes el mapa se quedaba en la vista del radar, sin ninguna referencia de dónde caía nada.
     */
    private void drawRequestRoutes(IncomingRequest request) {
        LatLng pickup = latLngOf(request.getPickupLat(), request.getPickupLng());
        if (pickup == null || !routePainter.isReady()) {
            return;
        }
        LatLng dropoff = latLngOf(request.getDropoffLat(), request.getDropoffLng());
        List<LatLng> stops = new ArrayList<>();
        for (Waypoint stop : request.getStops()) {
            stops.add(new LatLng(stop.getLat(), stop.getLng()));
        }
        routePainter.showRequestPreview(lastKnownLocation, pickup, stops, dropoff);
        if (lastKnownLocation != null) {
            return;
        }
        // Sin posición todavía (el bucle de ubicación acaba de arrancar): se pide una y se
        // repinta, para que el tramo de recogida no falte en la primera solicitud de la sesión.
        String rideId = request.getRideId();
        requestLastLocation(location -> {
            if (rideId.equals(displayedRideId) && routePainter.isReady()) {
                routePainter.showRequestPreview(location, pickup, stops, dropoff);
            }
        });
    }

    @Nullable
    private LatLng latLngOf(@Nullable Double lat, @Nullable Double lng) {
        return lat != null && lng != null ? new LatLng(lat, lng) : null;
    }

    private interface LocationConsumer {
        void accept(@Nullable LatLng location);
    }

    @SuppressLint("MissingPermission")
    private void requestLastLocation(LocationConsumer consumer) {
        if (!hasLocationPermission()) {
            consumer.accept(null);
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    }
                    consumer.accept(lastKnownLocation);
                })
                .addOnFailureListener(e -> consumer.accept(lastKnownLocation));
    }

    private void bindCounterOffers(IncomingRequest request, boolean readOnly) {
        containerIncomingCounters.removeAllViews();
        if (readOnly) {
            // Ya no hay nada que contraofertar sobre una oferta ya enviada.
            textIncomingCounterLabel.setVisibility(View.GONE);
            return;
        }
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
            button.setCornerRadius(dpToPx(20));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginEnd(dpToPx(6));
            button.setLayoutParams(params);
            double amount = request.getOffer() + increment;
            button.setOnClickListener(v ->
                    submitOffer(request.getRideId(), amount, request.getPassengerName(), false));
            containerIncomingCounters.addView(button);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /**
     * @param accepted true si es la oferta del pasajero tal cual (aceptar), false si es una
     *                 contraoferta. Solo cambia el copy del paso siguiente: para la API las dos
     *                 son el mismo POST /driver/rides/{id}/offer.
     */
    private void submitOffer(String rideId, double amount, String passengerName, boolean accepted) {
        displayedRideId = null;
        cancelIncomingExpiryTimer();
        // Vuelve al radar en el acto: postularse no bloquea al conductor y esperar en una pantalla
        // muerta le costaría los tres minutos de la subasta. El banner con esta oferta lo pinta la
        // bandeja en cuanto el servidor la marca (ver onInboxChanged).
        backToRadar();

        driverRepository.offerOnRide(rideId, amount, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(DriverHomeActivity.this,
                        getString(R.string.driver_offer_sent_toast_format,
                                String.format(Locale.getDefault(), "$%.2f", amount)),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(ApiException error) {
                // No salió: como no hay pantalla de espera que desmentir, basta con decirlo. La
                // solicitud sigue en la bandeja y se puede volver a intentar.
                ApiErrorCode code = error.getCode();
                int mensaje;
                if (code == ApiErrorCode.RIDE_ALREADY_TAKEN) {
                    mensaje = R.string.incoming_request_ride_taken_error;
                } else if (code == ApiErrorCode.TOO_MANY_LIVE_OFFERS) {
                    // No hizo nada mal: ya tiene el máximo de ofertas esperando respuesta.
                    mensaje = R.string.driver_too_many_live_offers;
                } else {
                    mensaje = R.string.incoming_request_offer_error;
                }
                Toast.makeText(DriverHomeActivity.this, mensaje, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void ignoreIncomingRequest() {
        if (displayedRideId != null) {
            // Solo del lado del teléfono: para el servidor esa solicitud sigue viva y otros
            // conductores pueden tomarla. Lo único que dice es "a mí no me la vuelvas a abrir".
            ignoredRideIds.add(displayedRideId);
        }
        closeDisplayedRequest();
    }

    /**
     * Cierra el panel sin marcar nada como ignorado — para la vista de solo lectura de una oferta
     * ya enviada, donde "Ignorar" no aplica (ver showIncomingRequest): no es una solicitud nueva
     * que descartar, solo se estaba repasando una ya en curso.
     */
    private void closeDisplayedRequest() {
        displayedRideId = null;
        cancelIncomingExpiryTimer();
        backToRadar();
    }

    /** Vuelve al panel de conexión desde la solicitud o desde la oferta enviada. */
    private void backToRadar() {
        cancelIncomingExpiryTimer();
        goTo(online ? Step.ONLINE : Step.OFFLINE);
        // drawRequestRoutes aleja la cámara para que quepa la ruta completa de la solicitud; al
        // salir (ofertada, ignorada o vencida) no hay nada que seguir mostrando ahí, así que la
        // cámara vuelve a acercarse a donde está el conductor.
        recenterMapOnDriver();
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
            lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastKnownLocation, 16f));
        });
    }

    private void startIncomingExpiryCountdown(String expiresAtIso) {
        cancelIncomingExpiryTimer();
        Long expiresAtMillis = parseInstantMillis(expiresAtIso);
        if (expiresAtMillis == null) {
            progressIncomingExpiry.setVisibility(View.GONE);
            textIncomingExpiry.setVisibility(View.GONE);
            return;
        }
        long totalMs = expiresAtMillis - System.currentTimeMillis();
        if (totalMs <= 0) {
            progressIncomingExpiry.setVisibility(View.GONE);
            textIncomingExpiry.setVisibility(View.GONE);
            return;
        }
        progressIncomingExpiry.setVisibility(View.VISIBLE);
        progressIncomingExpiry.setMax(1000);
        progressIncomingExpiry.setProgress(1000);
        textIncomingExpiry.setVisibility(View.VISIBLE);
        showRemainingTime(totalMs);
        incomingExpiryTimer = new CountDownTimer(totalMs, 200) {
            @Override
            public void onTick(long millisUntilFinished) {
                progressIncomingExpiry.setProgress((int) (1000 * millisUntilFinished / totalMs));
                showRemainingTime(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                progressIncomingExpiry.setProgress(0);
                textIncomingExpiry.setText(R.string.incoming_request_expired);
                // Venció mientras el conductor lo tenía en pantalla: se cierra solo en vez de
                // dejar una solicitud muerta con la que ya no se puede hacer nada. En modo
                // solo lectura no hay nada que ignorar —no era una solicitud nueva—, solo cerrar.
                if (step == Step.REQUEST) {
                    if (displayedRideReadOnly) {
                        closeDisplayedRequest();
                    } else {
                        ignoreIncomingRequest();
                    }
                }
            }
        };
        incomingExpiryTimer.start();
    }

    /**
     * Segundos que quedan, en m:ss. Se pinta en rojo en el último tramo: el color hace de aviso
     * sin tener que leer el número, que es de lo que se trata cuando quedan segundos.
     */
    private void showRemainingTime(long millisUntilFinished) {
        long totalSeconds = Math.max(0, millisUntilFinished / 1000);
        String clock = String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60);
        textIncomingExpiry.setText(getString(R.string.incoming_request_expiry_format, clock));
        textIncomingExpiry.setTextColor(totalSeconds <= EXPIRY_WARNING_SECONDS
                ? getColor(R.color.drivo_error)
                : com.bng.drivo.util.ColorUtils.resolveThemeColor(this,
                        com.google.android.material.R.attr.colorOnSurfaceVariant));
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

    // ---------------------------------------------------------------------------------------
    // Modal y medidas (mismo esquema que HomeFragment, sin la parte arrastrable)
    // ---------------------------------------------------------------------------------------

    /**
     * El listener NO se auto-remueve: el contenido del modal cambia solo (el saldo llega async,
     * la solicitud entrante crece con las contraofertas), así que el corte se recalcula en cada
     * pase de layout y no una única vez con el panel a medio llenar.
     */
    private void setUpBottomSheet() {
        sheetBehavior = BottomSheetBehavior.from(sheetContainer);
        sheetBehavior.setHideable(false);
        sheetBehavior.setSkipCollapsed(false);
        // No arrastrable en ningún estado: ningún panel del conductor guarda contenido extra que
        // mostrar al expandirlo, y dejarlo suelto solo permitiría taparse el mapa sin ganar nada.
        sheetBehavior.setDraggable(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::updateSheetStops;
        sheetContent.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    /** Recalcula dónde corta el modal y, con ese número, cuánta pantalla le queda libre al mapa. */
    private void updateSheetStops() {
        applyTopInsets();
        int collapsedHeightPx = sheetContainer.getVisibility() == View.VISIBLE
                ? clampPeek(sheetContent.getHeight()) : 0;
        if (collapsedHeightPx > 0 && collapsedHeightPx != lastCollapsedHeightPx) {
            lastCollapsedHeightPx = collapsedHeightPx;
            sheetBehavior.setPeekHeight(collapsedHeightPx, animateNextPeek);
            animateNextPeek = false;
        }
        updateMapViewport(Math.max(collapsedHeightPx, 0));
    }

    /**
     * Insets superiores explícitos, igual que en HomeFragment: el modal topa justo debajo de la
     * status bar (nunca detrás) y los flotantes se bajan para no quedar bajo ella. Acotar la
     * altura del sheet en vez de darle margen es lo que mantiene constante su geometría interna.
     */
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
        if (root.getHeight() > 0) {
            sheetAvailableHeightPx = availableHeightPx;
            if (sheetParams.height != availableHeightPx) {
                sheetParams.height = availableHeightPx;
                sheetContainer.setLayoutParams(sheetParams);
            }
        }

        int buttonMarginPx = Math.round(
                FLOATING_BUTTON_MARGIN_DP * getResources().getDisplayMetrics().density);
        setTopMargin(btnMenu, topInsetPx + buttonMarginPx);
        setTopMargin(btnMyLocation, topInsetPx + buttonMarginPx);
        setTopMargin(offlineBanner, topInsetPx + buttonMarginPx);
        setTopMargin(onlineBanner, topInsetPx + buttonMarginPx);
        // Los banners empiezan donde termina la fila de flotantes, para no montarse sobre el
        // menú ni sobre el aviso de conectividad.
        int buttonSizePx = Math.round(
                FLOATING_BUTTON_SIZE_DP * getResources().getDisplayMetrics().density);
        setTopMargin(containerOfferBanners, topInsetPx + buttonMarginPx * 2 + buttonSizePx);
    }

    /**
     * El corte del modal nunca puede alcanzar su propio alto: si lo iguala, el panel ocupa la
     * pantalla entera y tapa el mapa por completo. Mismo tope que en el modal del pasajero, donde
     * además dejaba el arrastre sin recorrido — ver HomeFragment.clampPeek().
     */
    private int clampPeek(int peekPx) {
        if (sheetAvailableHeightPx <= 0) {
            return peekPx;
        }
        int minTravelPx = Math.round(
                SHEET_MIN_EXPAND_TRAVEL_DP * getResources().getDisplayMetrics().density);
        int maxPeekPx = sheetAvailableHeightPx - minTravelPx;
        return maxPeekPx > 0 ? Math.min(peekPx, maxPeekPx) : peekPx;
    }

    private void setTopMargin(View view, int topMarginPx) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.topMargin != topMarginPx) {
            params.topMargin = topMarginPx;
            view.setLayoutParams(params);
        }
    }

    /**
     * Le dice al mapa cuánto le tapa el modal, para que su centro lógico (y el encuadre de
     * animateCamera/reframe) caiga en el hueco visible en vez de en la pantalla completa. El
     * radar ya no necesita nada de esto: al ser Circle anclados a lastKnownLocation, el SDK los
     * dibuja en su posición geográfica real sin importar cuánto padding tenga el mapa.
     */
    private void updateMapViewport(int sheetHeightPx) {
        // Los banners también tapan mapa: sin sumarlos, la ruta de una solicitud nueva se
        // encuadraría por detrás de las ofertas que ya están en pantalla.
        int topPaddingPx = sheetTopInsetPx + bannersHeightPx();
        if (googleMap != null
                && (sheetHeightPx != lastMapBottomPaddingPx || topPaddingPx != lastMapTopPaddingPx)) {
            lastMapBottomPaddingPx = sheetHeightPx;
            lastMapTopPaddingPx = topPaddingPx;
            googleMap.setPadding(0, topPaddingPx, 0, sheetHeightPx);
            // El panel de la solicitud es más alto que el de conexión, y la ruta se dibuja antes
            // de que el modal termine de crecer: sin este reencuadre quedaría medio tapada.
            if (step == Step.REQUEST && routePainter.isReady()) {
                routePainter.reframe();
            }
        }
    }

    /** Lo que ocupan los banners, contando desde el borde del mapa (incluye su margen superior). */
    private int bannersHeightPx() {
        if (containerOfferBanners.getChildCount() == 0) {
            return 0;
        }
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) containerOfferBanners.getLayoutParams();
        return Math.max(0, params.topMargin - sheetTopInsetPx) + containerOfferBanners.getHeight();
    }

    // ---------------------------------------------------------------------------------------
    // Mapa, ubicación y conectividad
    // ---------------------------------------------------------------------------------------

    /**
     * El mapa y el permiso de ubicación se piden aquí, no en onCreate(): un conductor sin aprobar
     * todavía no tiene acceso a esa parte de la app, así que ni el mapa se infla ni el diálogo de
     * permiso aparece hasta que el gate confirma "approved". mapInitialized evita repetir la
     * transacción si el gate se vuelve a correr (p. ej. al reintentar).
     */
    private void setUpMapAndLocationIfNeeded() {
        if (mapInitialized) {
            return;
        }
        mapInitialized = true;

        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.map_container, mapFragment).commit();
        mapFragment.getMapAsync(this);

        if (!hasLocationPermission()) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(this, googleMap);
        googleMap.setPadding(0, sheetTopInsetPx, 0, Math.max(lastCollapsedHeightPx, 0));
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_container);
        routePainter.attach(googleMap, mapFragment != null ? mapFragment.getView() : null);
        createRadarCircles();
        createSelfMarker();
        if (hasLocationPermission()) {
            enableMyLocation();
        }
    }

    /**
     * Los anillos del radar como overlays reales del mapa (Circle), no como View sobre pantalla.
     * Nacen invisibles y sin centro útil: setRadarVisible()/updateRadarCenter() los ponen al día
     * en cuanto hay paso ONLINE y/o ubicación conocida.
     */
    private void createRadarCircles() {
        if (googleMap == null || radarRingOuter != null) {
            return;
        }
        LatLng center = lastKnownLocation != null ? lastKnownLocation : new LatLng(0, 0);
        int successColor = ContextCompat.getColor(this, R.color.drivo_success);
        radarRingOuter = googleMap.addCircle(new CircleOptions()
                .center(center)
                .radius(RADAR_OUTER_BASE_RADIUS_METERS)
                .strokeWidth(0f)
                .fillColor(withAlpha(successColor, 0.15f))
                .visible(false));
        radarRingInner = googleMap.addCircle(new CircleOptions()
                .center(center)
                .radius(RADAR_INNER_BASE_RADIUS_METERS)
                .strokeWidth(0f)
                .fillColor(withAlpha(successColor, 0.25f))
                .visible(false));
        setRadarVisible(radarVisibleRequested);
    }

    private void setRadarVisible(boolean visible) {
        radarVisibleRequested = visible;
        if (radarRingOuter != null) {
            radarRingOuter.setVisible(visible);
        }
        if (radarRingInner != null) {
            radarRingInner.setVisible(visible);
        }
    }

    private void updateRadarCenter(LatLng latLng) {
        if (radarRingOuter != null) {
            radarRingOuter.setCenter(latLng);
        }
        if (radarRingInner != null) {
            radarRingInner.setCenter(latLng);
        }
    }

    private static int withAlpha(int colorArgb, float alpha) {
        int a = Math.round(alpha * 255) << 24;
        return (colorArgb & 0x00FFFFFF) | a;
    }

    /**
     * Nace invisible y sin posición útil, igual que los Circle del radar — se revela en cuanto
     * hay ubicación real (ver {@link #updateSelfMarkerPosition}), nunca en (0,0).
     */
    private void createSelfMarker() {
        if (googleMap == null || selfMarker != null) {
            return;
        }
        selfMarker = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(0, 0))
                .icon(MarkerIconFactory.carMarker(this, R.color.drivo_vehicle_body))
                .anchor(0.5f, 0.5f)
                .flat(true)
                .visible(false));
    }

    private void updateSelfMarkerPosition(LatLng position, @Nullable Double headingDegrees) {
        if (selfMarker == null) {
            return;
        }
        selfMarker.setPosition(position);
        if (headingDegrees != null) {
            selfMarker.setRotation(headingDegrees.floatValue());
        }
        updateSelfMarkerVisibility();
    }

    /** Oculto solo durante REQUEST: ahí el mismo coche ya lo pinta routePainter. */
    private void updateSelfMarkerVisibility() {
        if (selfMarker == null || lastKnownLocation == null) {
            return;
        }
        selfMarker.setVisible(step != Step.REQUEST);
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (googleMap == null) {
            return;
        }
        // El coche propio (selfMarker) reemplaza al punto azul del SDK — se queda apagado.
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                return;
            }
            lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());
            updateRadarCenter(lastKnownLocation);
            Double heading = location.hasBearing() ? (double) location.getBearing() : null;
            updateSelfMarkerPosition(lastKnownLocation, heading);
            // Con una solicitud en pantalla manda su encuadre: recentrar aquí desharía la vista
            // de las dos rutas justo después de dibujarlas.
            if (googleMap != null && step != Step.REQUEST) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastKnownLocation, 16f));
            }
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Un conductor sin señal deja de reportar ubicación y de recibir solicitudes sin enterarse:
     * el banner es la única pista de por qué el radar se quedó callado.
     */
    private void onConnectivityChanged(boolean isOnline) {
        showOfflineBanner(!isOnline);
        if (!isOnline) {
            wasOffline = true;
            return;
        }
        if (wasOffline) {
            wasOffline = false;
            showReconnectedBanner();
            // Al volver la señal se refresca lo que pudo perderse mientras no había red.
            if (approved) {
                loadWallet();
            }
        }
    }

    private void showOfflineBanner(boolean show) {
        if ((offlineBanner.getVisibility() == View.VISIBLE) == show) {
            return;
        }
        if (show) {
            offlineBanner.setAlpha(0f);
            offlineBanner.setVisibility(View.VISIBLE);
            offlineBanner.animate().alpha(1f).setDuration(BANNER_FADE_MS).start();
        } else {
            offlineBanner.animate().alpha(0f).setDuration(BANNER_FADE_MS)
                    .withEndAction(() -> offlineBanner.setVisibility(View.GONE)).start();
        }
    }

    private void showReconnectedBanner() {
        reconnectedBannerHandler.removeCallbacksAndMessages(null);
        onlineBanner.animate().cancel();
        onlineBanner.setAlpha(0f);
        onlineBanner.setVisibility(View.VISIBLE);
        onlineBanner.animate().alpha(1f).setDuration(BANNER_FADE_MS).start();
        reconnectedBannerHandler.postDelayed(() ->
                onlineBanner.animate().alpha(0f).setDuration(BANNER_FADE_MS)
                        .withEndAction(() -> onlineBanner.setVisibility(View.GONE)).start(),
                RECONNECTED_BANNER_VISIBLE_MS);
    }

    /**
     * Corre siempre que la pantalla está visible, sin importar online/offline — antes de
     * selfMarker esto lo resolvía gratis el punto azul del SDK, que sigue a la ubicación real
     * incluso desconectado; al quitarlo, si este loop se quedaba atado a online (como el
     * reporte al servidor, que sí debe seguir estándolo) el coche se congelaba en offline. Idem
     * y protegido contra permiso ausente porque ahora se llama desde onStart() sin ese filtro.
     */
    @SuppressLint("MissingPermission")
    private void startLocationLoop() {
        if (locationCallback != null || !hasLocationPermission()) {
            return;
        }
        // HIGH_ACCURACY y no BALANCED: BALANCED se resuelve por el proveedor de red, que puede
        // estar apagado o no existir (en el emulador lo está), y entonces la petición nunca llega
        // al GPS y no entra ni una sola lectura — el coche se queda clavado donde apareció.
        // El punto azul que este marcador reemplaza pedía exactamente HIGH_ACCURACY cada 5 s, así
        // que igualarlo no es gastar de más: es dejar de depender de él para lo mismo.
        LocationRequest request = new LocationRequest.Builder(LOCATION_INTERVAL_IDLE_MS)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(LOCATION_INTERVAL_IDLE_MS)
                .build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                android.location.Location location = result.getLastLocation();
                if (location == null) {
                    return;
                }
                lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());
                updateRadarCenter(lastKnownLocation);
                Double heading = location.hasBearing() ? (double) location.getBearing() : null;
                if (step == Step.REQUEST && routePainter.isReady()) {
                    routePainter.updateDriverPosition(lastKnownLocation);
                } else {
                    updateSelfMarkerPosition(lastKnownLocation, heading);
                }
                // El reporte al servidor sí se queda solo para online: un conductor desconectado
                // no debe ocupar el radar de nadie ni dejar rastro en el backend.
                if (!online) {
                    return;
                }
                // El mapa se refresca a 5 s, pero al servidor se le sigue hablando cada 12 s.
                long nowMs = SystemClock.elapsedRealtime();
                if (nowMs - lastLocationReportAtMs < LOCATION_REPORT_INTERVAL_MS) {
                    return;
                }
                lastLocationReportAtMs = nowMs;
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

    /**
     * Animación puramente decorativa (dos anillos que laten) — mismo patrón que el radar del
     * pasajero. Arranca en onCreate() aunque los Circle todavía no existan (nacen en
     * createRadarCircles(), cuando el mapa está listo): cada frame revisa null antes de tocarlos,
     * así que el animador simplemente no pinta nada hasta que el mapa aparece.
     */
    private void startRadarPulse() {
        int successColor = ContextCompat.getColor(this, R.color.drivo_success);
        radarAnimator = ValueAnimator.ofFloat(0f, 1f);
        radarAnimator.setDuration(RADAR_PULSE_DURATION_MS);
        radarAnimator.setRepeatCount(ValueAnimator.INFINITE);
        radarAnimator.setInterpolator(new LinearInterpolator());
        radarAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            float scale = 0.85f + fraction * 0.3f;
            if (radarRingOuter != null) {
                radarRingOuter.setRadius(RADAR_OUTER_BASE_RADIUS_METERS * scale);
                radarRingOuter.setFillColor(withAlpha(successColor, 0.2f * (1f - fraction)));
            }
            float innerFraction = (fraction + 0.5f) % 1f;
            float innerScale = 0.85f + innerFraction * 0.3f;
            if (radarRingInner != null) {
                radarRingInner.setRadius(RADAR_INNER_BASE_RADIUS_METERS * innerScale);
                radarRingInner.setFillColor(withAlpha(successColor, 0.3f * (1f - innerFraction)));
            }
        });
        radarAnimator.start();
    }

    // ---------------------------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        connectivitySubscription = connectivityRepository.observe(this::onConnectivityChanged);
        // El coche propio se sigue moviendo online u offline (ver el javadoc de
        // startLocationLoop()); la bandeja de solicitudes sí es exclusiva de online.
        startLocationLoop();
        if (online) {
            startInboxListener();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Volvimos a Inicio, sea por el botón atrás o porque la otra pantalla se cerró sola.
        navView.setCheckedItem(R.id.nav_driver_inicio);
        // Las medidas del modal se reescriben solo cuando cambian, así que una calculada con la
        // ventana a medio restaurar (volver del bloqueo de pantalla) se quedaría pegada. Se tiran
        // y se fuerza un pase con la geometría ya asentada.
        lastCollapsedHeightPx = -1;
        findViewById(android.R.id.content).requestLayout();
        // Cubre volver de un viaje ya cerrado o de Ganancias: el saldo pudo cambiar.
        if (approved) {
            loadWallet();
            // Y si mientras no mirábamos un pasajero nos eligió, el viaje se abre aquí. Es lo que
            // vuelve al push un atajo en vez de la única vía: sin esto, una notificación perdida
            // dejaba al conductor asignado —y por tanto fuera del radar— sin viaje en pantalla.
            openingWonRide = false;
            checkCurrentRide(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connectivitySubscription != null) {
            connectivitySubscription.stop();
            connectivitySubscription = null;
        }
        reconnectedBannerHandler.removeCallbacksAndMessages(null);
        stopBannerTicker();
        stopLocationLoop();
        if (online) {
            stopInboxListener();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        routePainter.detach();
        if (radarAnimator != null) {
            radarAnimator.cancel();
        }
        cancelIncomingExpiryTimer();
        stopBannerTicker();
    }
}
