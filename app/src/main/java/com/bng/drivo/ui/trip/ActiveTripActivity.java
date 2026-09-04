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
import com.bng.drivo.ui.map.PolylineDecoder;
import com.bng.drivo.util.NavHeaderRating;
import com.bng.drivo.ui.map.MarkerIconFactory;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    /**
     * Calificación del conductor elegido. Es el mismo número que el pasajero vio en la tarjeta con
     * la que lo eligió, y aquí sirve para reconocerla: sin ella, la pantalla del viaje enseña menos
     * de su conductor que la lista de la que salió. Ausente si el servidor no la mandó.
     */
    public static final String EXTRA_DRIVER_RATING = "extra_driver_rating";
    public static final String EXTRA_DRIVER_DETAILS = "extra_driver_details";
    public static final String EXTRA_PRICE = "extra_price";
    public static final String EXTRA_ORIGIN = "extra_origin";
    public static final String EXTRA_DESTINATION = "extra_destination";
    /**
     * Nombre de la dirección guardada de la que salió el destino ("Casa", "Trabajo"), o null si el
     * pasajero lo eligió en el buscador o con el pin. Es cosa suya y no sale de su app: el conductor
     * ve la dirección, nunca cómo la tiene guardada.
     */
    public static final String EXTRA_DESTINATION_LABEL = "extra_destination_label";
    public static final String EXTRA_ORIGIN_LAT = "extra_origin_lat";
    public static final String EXTRA_ORIGIN_LNG = "extra_origin_lng";
    public static final String EXTRA_DESTINATION_LAT = "extra_destination_lat";
    public static final String EXTRA_DESTINATION_LNG = "extra_destination_lng";
    /** Parada intermedia opcional: (0,0) significa "sin parada" (el flujo admite una sola). */
    public static final String EXTRA_STOP_LAT = "extra_stop_lat";
    public static final String EXTRA_STOP_LNG = "extra_stop_lng";
    /**
     * Trazo de la ruta por calles, codificado, tal como lo devolvió la API al aceptar la oferta.
     * Viaja en el Intent y no se vuelve a pedir: son un par de KB y el viaje ya está cerrado, así
     * que el trazo no puede cambiar. Ausente —o ilegible— se cae a la guía recta.
     */
    public static final String EXTRA_POLYLINE = "extra_polyline";

    private static final LatLng DEFAULT_POSITION = new LatLng(19.4326, -99.1332);
    /** Lo bastante cerca para ver en qué calle va el coche, sin perder las de alrededor. */
    private static final float LOCATE_ZOOM = 16f;
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
    /**
     * Último ETA publicado por el servidor, en minutos. Se guarda porque el número llega por el
     * canal en vivo y las fases cambian por otro lado: sin esto, entrar a IN_PROGRESS dejaría el
     * tile vacío hasta la siguiente posición del conductor.
     */
    @Nullable
    private Integer etaMin;
    private View groupTripEta;
    private TextView textTripEta;
    private View btnLocateDriver;
    /**
     * Si la cámara va pegada al coche. Empieza encendido —el pasajero abre esta pantalla para ver
     * por dónde viene— y lo apaga él mismo al arrastrar el mapa. El botón lo reengancha.
     */
    private boolean followingDriver = true;
    private View bottomStack;
    /** Último alto aplicado como padding del mapa; evita repetir el trabajo en cada pase. */
    private int lastMapBottomPaddingPx = -1;
    private ValueAnimator driverAnimator;
    private Polyline routePolyline;
    /** Recorrido real por calles ya decodificado. Vacío si el servidor no mandó trazo. */
    private List<LatLng> routePoints = Collections.emptyList();

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
        routePoints = PolylineDecoder.decode(getIntent().getStringExtra(EXTRA_POLYLINE));

        if (rideId == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.text_driver_avatar)).setText(driverInitials);
        bindDriverName();
        ((TextView) findViewById(R.id.text_driver_details)).setText(driverDetails);
        ((TextView) findViewById(R.id.text_trip_price)).setText(String.format(Locale.getDefault(), "$%.2f", price));
        bindDestination();

        bottomStack = findViewById(R.id.container_trip_bottom);
        bottomStack.getViewTreeObserver().addOnGlobalLayoutListener(this::applyMapPadding);
        btnLocateDriver = findViewById(R.id.btn_locate_driver);
        btnLocateDriver.setOnClickListener(v -> locateDriver());
        groupTripEta = findViewById(R.id.group_trip_eta);
        textTripEta = findViewById(R.id.text_trip_eta);
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

    /**
     * El destino del viaje, con el mismo criterio que la tarjeta de "Viaje solicitado": si salió de
     * una dirección guardada manda su nombre y la dirección baja a una segunda línea. El nombre
     * solo dice cuál de sus direcciones eligió, no a dónde va, y aquí el pasajero quiere lo uno y
     * lo otro durante todo el viaje.
     */
    /**
     * Nombre y calificación del conductor, compuestos igual que en la tarjeta de oferta con la que
     * el pasajero lo eligió ({@code SearchingPanel.buildDriverCard}): "Juan P. · ★4.9", con el
     * vehículo debajo. Repetir ahí la misma línea es lo que le permite reconocer, ya en el viaje,
     * a cuál de los conductores que le ofertaron aceptó.
     */
    /**
     * El conductor en la tarjeta: nombre en su línea y calificación montada sobre el avatar.
     *
     * <p>La calificación no va junto al nombre —donde estuvo primero— porque ahí compite con él y
     * con el vehículo de debajo. Sobre el avatar es donde ya vive en el resto de la app
     * (part_nav_avatar_rating.xml), con el mismo fondo y el mismo recorte.
     */
    private void bindDriverName() {
        ((TextView) findViewById(R.id.text_driver_name)).setText(driverName);

        TextView badge = findViewById(R.id.text_driver_rating);
        double rating = getIntent().getDoubleExtra(EXTRA_DRIVER_RATING, 0);
        if (rating <= 0) {
            // Sin dato la pastilla desaparece entera, ni guion ni hueco — mismo criterio que
            // NavHeaderRating: en una tarjeta que se repinta con cada fase, un adorno que unas
            // veces es número y otras no, parpadea.
            badge.setVisibility(View.GONE);
            return;
        }
        // El texto lo compone NavHeaderRating y no este archivo: es la única forma de que la
        // pastilla del viaje y la del cajón no se contradigan a la primera corrección de copy.
        // Los viajes del conductor no viajan en el contrato, así que van null y se pinta el
        // número, que es lo único que se puede afirmar.
        badge.setText(NavHeaderRating.text(this, rating, null));
        badge.setVisibility(View.VISIBLE);
    }

    private void bindDestination() {
        TextView destination = findViewById(R.id.text_trip_destination);
        TextView destinationAddress = findViewById(R.id.text_trip_destination_address);
        String address = getIntent().getStringExtra(EXTRA_DESTINATION);
        String label = getIntent().getStringExtra(EXTRA_DESTINATION_LABEL);

        if (label == null || label.trim().isEmpty()) {
            destination.setText(address);
            destinationAddress.setVisibility(View.GONE);
            return;
        }
        destination.setText(label);
        destinationAddress.setText(address);
        destinationAddress.setVisibility(View.VISIBLE);
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
        // El mapa puede llegar después del primer pase de medida, así que el padding se aplica
        // también aquí: si solo se hiciera al medir, la primera cámara encuadraría contra la
        // pantalla completa.
        lastMapBottomPaddingPx = -1;
        applyMapPadding();
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.fromLatLngZoom(originLatLng, 14f)));

        // Solo el arrastre del pasajero suelta la cámara; los encuadres que hace la app llegan con
        // otro motivo y no deben apagar el seguimiento.
        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                followingDriver = false;
            }
        });

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
        // El subtítulo que se acaba de poner es el respaldo: si ya hay ETA, lo sustituye.
        bindEta();

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
        bindEta();

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

        if (routePoints.size() >= 2) {
            routePolyline = googleMap.addPolyline(new PolylineOptions()
                    .addAll(routePoints)
                    .width(12f)
                    .color(getColor(R.color.drivo_success))
                    .jointType(JointType.ROUND)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap()));
        } else {
            // Respaldo: el servidor no mandó trazo. Punteada a propósito, para no dar por camino
            // real una línea que solo une los extremos.
            List<PatternItem> dashed = Arrays.asList(new Dash(20f), new Gap(12f));
            routePolyline = googleMap.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .width(8f)
                    .color(getColor(R.color.drivo_success))
                    .pattern(dashed));
        }

        frameForCurrentPhase();
    }

    /**
     * Pone el tiempo que falta donde le toca según la fase: en el subtítulo mientras el conductor
     * viene, y en su propio tile una vez a bordo.
     *
     * <p>Sin dato no se enseña nada —ni "--" ni un cero—: el ETA lo calcula el servidor y puede no
     * venir, y un hueco es más honesto que un número inventado. En DRIVER_ARRIVED tampoco se
     * enseña: el conductor ya está en el punto, ahí lo que corre es el cronómetro de cortesía.
     */
    private void bindEta() {
        boolean inProgress = "IN_PROGRESS".equals(currentStatus);
        groupTripEta.setVisibility(inProgress && etaMin != null ? View.VISIBLE : View.GONE);
        if (inProgress) {
            if (etaMin != null) {
                textTripEta.setText(getString(R.string.eta_approx_min, etaMin));
            }
            return;
        }
        if ("MATCHED".equals(currentStatus) && etaMin != null) {
            textStatusSubtitle.setText(getString(R.string.active_trip_status_en_route_eta, etaMin));
        }
    }

    /**
     * Le dice al mapa cuánto de él queda tapado por abajo.
     *
     * <p>El SDK encuadra y centra contra el rectángulo que le quede libre, no contra la vista
     * entera, así que con esto todo lo demás —el encuadre inicial, el seguimiento, el botón de
     * localizar— cae solo en su sitio. Sin ello, "centrar al conductor" lo centraba en la pantalla
     * y la tarjeta se lo comía: el coche quedaba justo detrás de ella.
     *
     * <p>Se remide en cada pase porque la tarjeta cambia de alto con la fase: el cronómetro de
     * cortesía la estira y el bloque de precio y llegada la cambia otra vez.
     */
    private void applyMapPadding() {
        if (googleMap == null || bottomStack == null) {
            return;
        }
        int heightPx = bottomStack.getHeight();
        if (heightPx <= 0 || heightPx == lastMapBottomPaddingPx) {
            return;
        }
        lastMapBottomPaddingPx = heightPx;
        googleMap.setPadding(0, 0, 0, heightPx);

        // El hueco visible acaba de cambiar de tamaño, así que lo que estuviera centrado ya no lo
        // está. Se rehace la intención de cámara vigente en vez de dejarla a medias.
        if (followingDriver && driverPosition != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(driverPosition));
        } else if (driverPosition != null || routePolyline != null) {
            frameForCurrentPhase();
        }
    }

    /**
     * Lleva la cámara al coche del conductor.
     *
     * <p>El mapa se puede arrastrar en las tres fases del viaje, y mirar alrededor —qué hay cerca
     * del punto de encuentro, por dónde va la ruta— era hasta ahora un viaje de ida: el único
     * reencuadre automático es el primero, cuando aparece el coche. Este botón es la vuelta.
     *
     * <p>Centra sobre el conductor y no sobre la ruta entera a propósito: lo que se pierde de vista
     * al arrastrar, y lo que el pasajero quiere recuperar, es dónde está el auto.
     *
     * <p>Además vuelve a enganchar la cámara al coche: quien lo toca no quiere una foto del sitio
     * donde está ahora, quiere volver a acompañarlo.
     */
    private void locateDriver() {
        followingDriver = true;
        if (googleMap == null || driverPosition == null) {
            return;
        }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverPosition, LOCATE_ZOOM));
    }

    private void addRouteMarker(LatLng position, int colorRes) {
        googleMap.addMarker(new MarkerOptions()
                .position(position)
                .icon(MarkerIconFactory.circle(this, colorRes, 16))
                .anchor(0.5f, 0.5f));
    }

    private void onDriverLocationChanged(double lat, double lng, @Nullable Integer etaMin) {
        this.etaMin = etaMin;
        bindEta();
        // El ETA se pinta aunque el mapa todavía no exista: es del modal, no del mapa, y el
        // pasajero que abre la pantalla quiere el número antes que el coche.
        if (googleMap == null) {
            return;
        }
        LatLng newPosition = new LatLng(lat, lng);

        // Solo tiene sentido ofrecerlo cuando hay algo que localizar.
        btnLocateDriver.setVisibility(View.VISIBLE);

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

        // La cámara acompaña al coche desde que viene por el pasajero hasta que lo deja. Se mueve
        // el centro y no el zoom: el acercamiento que el pasajero haya elegido es suyo.
        if (followingDriver) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(newPosition));
        }
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
            // La ruta real puede salirse del rectángulo origen-destino; sin esto quedaría cortada.
            for (LatLng point : routePoints) {
                bounds.include(point);
            }
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
