package com.bng.drivo.ui.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bng.drivo.R;
import com.bng.drivo.data.model.AddressLabel;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AddressRepository;
import com.bng.drivo.data.repository.ConnectivityRepository;
import com.bng.drivo.data.repository.FirestoreRideRealtimeRepository;
import com.bng.drivo.data.repository.RealtimeSubscription;
import com.bng.drivo.data.repository.RestAddressRepository;
import com.bng.drivo.data.repository.SystemConnectivityRepository;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.service.PlacesAutocompleteService;
import com.bng.drivo.ui.destination.PickLocationPanel;
import com.bng.drivo.ui.map.MapPresenter;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.ui.price.ConfirmPricePanel;
import com.bng.drivo.ui.search.SearchingPanel;
import com.bng.drivo.ui.trip.ActiveTripActivity;
import com.bng.drivo.ui.trips.TripDetailBottomSheet;
import com.bng.drivo.ui.trips.TripHistoryRowBinder;
import com.bng.drivo.util.ColorUtils;
import com.bng.drivo.util.PrefsHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Host del flujo del pasajero sobre un único mapa: home, confirmación de tarifa y radar de
 * conductores ocurren todos aquí, cambiando solo el contenido del modal y las capas flotantes.
 *
 * <p>Antes eran tres Activities encadenadas (esta pantalla → ConfirmPriceActivity →
 * SearchingDriverActivity), cada una con su propio SupportMapFragment: el mapa se creaba,
 * estilizaba y encuadraba desde cero en cada salto, y eso se veía como un corte aunque la cámara
 * arrancara ya en la ubicación correcta. Ahora el mapa es uno solo y de larga vida
 * ({@link MapPresenter}), el estado del viaje vive en {@link TripFlowViewModel}, y cada paso es un
 * panel dentro de este mismo modal ({@link ConfirmPricePanel}, {@link SearchingPanel}). Cambiar de
 * paso es un fundido del modal más un vuelo de cámara — nunca una transición de pantalla.
 *
 * <p>Consecuencia práctica: ninguna tarjeta tiene que adivinar cuánto espacio le queda. El modal
 * se mide solo (ver {@link #updateSheetStops}) y le pasa al mapa el alto real de lo que tapa
 * arriba y abajo, así que el encuadre de la ruta cae siempre dentro de la zona visible.
 *
 * <p>En el paso IDLE el modal es persistente y arrastrable en 2 niveles: colapsado corta justo
 * después de las direcciones guardadas ({@code group_peek_content} + {@link #PEEK_ADDRESS_ROWS}
 * filas, medido en tiempo real) y expandido llega al tope. En los otros pasos se fija al alto de
 * su panel y no se arrastra.
 */
public class HomeFragment extends Fragment implements OnMapReadyCallback {

    // Ciudad de México como origen por defecto, antes de obtener la ubicación real.
    private static final LatLng DEFAULT_POSITION = new LatLng(19.4326, -99.1332);
    private static final String PREF_KEY_CAMERA = "home_camera_position";
    private static final long SEARCH_DEBOUNCE_MS = 300;
    private static final long BANNER_FADE_MS = 200;
    private static final long RECONNECTED_BANNER_VISIBLE_MS = 2500;
    /** Fundido del contenido del modal al cambiar de paso (mitad de salida, mitad de entrada). */
    private static final long PANEL_FADE_OUT_MS = 110;
    private static final long PANEL_FADE_IN_MS = 190;
    /** Fundido del ícono del botón de navegación al alternar entre menú y atrás. */
    private static final long NAV_ICON_FADE_MS = 90;

    private FusedLocationProviderClient fusedLocationClient;
    private GoogleMap googleMap;
    private BottomSheetBehavior<View> sheetBehavior;
    private LatLng originLocation = DEFAULT_POSITION;

    private TripFlowViewModel viewModel;
    private MapPresenter mapPresenter;
    private PickLocationPanel pickLocationPanel;
    private ConfirmPricePanel confirmPricePanel;
    private SearchingPanel searchingPanel;
    private View panelHome;
    private View panelPickLocation;
    private View panelConfirmPrice;
    private View panelSearching;
    private View sheetContent;
    private View routeCard;
    private View pickLocationPin;
    private View radarOverlay;

    private AddressRepository addressRepository;
    private ConnectivityRepository connectivityRepository;
    @Nullable
    private RealtimeSubscription connectivitySubscription;
    private View offlineBanner;
    private View onlineBanner;
    private final Handler reconnectedBannerHandler = new Handler(Looper.getMainLooper());
    // true solo entre un evento offline real y el siguiente online — así el banner verde de
    // reconexión no aparece en el primer estado "online" al abrir la app con señal, que nunca
    // estuvo caída.
    private boolean wasOffline;
    // Qué secciones tienen ya sus datos. Al recuperar la señal se reintenta solo lo que falta,
    // en vez de volver a pedirlo todo: menos datos y sin parpadeo de lo que ya se ve.
    private boolean greetingLoaded;
    private boolean savedAddressesLoaded;
    private boolean recentTripsLoaded;
    private final PlacesAutocompleteService placesAutocompleteService = new PlacesAutocompleteService(this);
    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchRunnable;
    private boolean searchModeActive;
    private OnBackPressedCallback backCallback;
    private int lastCollapsedHeightPx = -1;
    private int sheetTopInsetPx;
    /**
     * Paso cuyo panel está realmente puesto en el modal. Va un fundido por detrás de
     * {@code viewModel.getStep()} y es el que manda al medir: durante la transición el modal
     * todavía contiene el panel viejo, y medir contra el paso nuevo daría un alto que no
     * corresponde a nada de lo que hay en pantalla.
     */
    private TripFlowViewModel.Step displayedStep = TripFlowViewModel.Step.IDLE;
    /** Papel actual del botón flotante (menú vs. atrás); null hasta el primer pintado. */
    @Nullable
    private Boolean navButtonIdle;
    /** Solo el primer ajuste tras cambiar de paso se anima; los demás son remedidas de rutina. */
    private boolean animateNextPeek;
    private View statusBarScrim;
    @Nullable
    private GradientDrawable sheetBackground;
    private float sheetCornerRadiusPx;
    /** Reutilizado en cada frame de onSlide para no asignar un array por fotograma. */
    private final float[] sheetCornerRadii = new float[8];

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        addressRepository = new RestAddressRepository(requireContext());
        connectivityRepository = new SystemConnectivityRepository(requireContext());
        offlineBanner = view.findViewById(R.id.banner_offline);
        onlineBanner = view.findViewById(R.id.banner_online);

        viewModel = new ViewModelProvider(requireActivity()).get(TripFlowViewModel.class);
        mapPresenter = new MapPresenter(requireContext());

        panelHome = view.findViewById(R.id.panel_home);
        panelPickLocation = view.findViewById(R.id.panel_pick_location);
        panelConfirmPrice = view.findViewById(R.id.panel_confirm_price);
        panelSearching = view.findViewById(R.id.panel_searching);
        sheetContent = view.findViewById(R.id.sheet_content);
        routeCard = view.findViewById(R.id.card_route_summary);
        pickLocationPin = view.findViewById(R.id.img_pick_location_pin);
        radarOverlay = view.findViewById(R.id.layout_radar);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setUpPanels(view);
        setUpBackHandling(view);
        setUpBottomSheet(view);
        setUpDestinationSearch(view);
        loadGreeting(view);
        loadSavedAddresses(view);
        loadRecentTrips(view);

        view.findViewById(R.id.btn_open_drawer).setOnClickListener(v -> onNavButtonClicked());
        view.findViewById(R.id.btn_my_location).setOnClickListener(v -> {
            if (hasLocationPermission()) {
                showMyLocation();
            } else {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });

        // Pinta el paso actual sin animar: cubre igual el primer arranque (IDLE) y la vuelta de
        // un cambio de configuración a mitad del flujo, que es justo lo que el ViewModel guarda.
        applyStep(viewModel.getStep(), false);
        viewModel.setStepListener(step -> applyStep(step, true));

        if (hasLocationPermission()) {
            // Ya resuelto de una sesión anterior: no hay diálogo de por medio, es seguro
            // encadenar el permiso de notificaciones de inmediato.
            requestNotificationPermission();
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void setUpPanels(View root) {
        TripRepository tripRepository = new RestTripRepository(requireContext());

        pickLocationPanel = new PickLocationPanel(panelPickLocation, pickLocationPin, mapPresenter,
                (address, lat, lng) -> {
                    if (isAdded()) {
                        startTripFlow(address, lat, lng);
                    }
                });

        confirmPricePanel = new ConfirmPricePanel(panelConfirmPrice, routeCard, viewModel, mapPresenter,
                tripRepository, placesAutocompleteService, new ConfirmPricePanel.Callbacks() {
            @Override
            public void onRideCreated(@NonNull Ride ride) {
                if (isAdded()) {
                    viewModel.goTo(TripFlowViewModel.Step.SEARCHING);
                }
            }

            @Override
            public void onQuoteFailed() {
                if (isAdded()) {
                    returnToIdle();
                }
            }
        });

        searchingPanel = new SearchingPanel(panelSearching, radarOverlay, viewModel, tripRepository,
                new FirestoreRideRealtimeRepository(), new SearchingPanel.Callbacks() {
            @Override
            public void onOfferAccepted(@NonNull Ride ride) {
                if (isAdded()) {
                    goToActiveTrip(ride);
                }
            }

            @Override
            public void onSearchCancelled() {
                if (isAdded()) {
                    returnToIdle();
                }
            }
        });
    }

    /**
     * Un único callback de "atrás" para todo el flujo, que despacha según el paso — el sistema
     * solo entrega el gesto a uno, y tener callbacks separados por paso obligaría a habilitarlos
     * y deshabilitarlos en cadena.
     */
    private void setUpBackHandling(View root) {
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (searchModeActive) {
                    cancelSearch(root);
                    return;
                }
                TripFlowViewModel.Step step = viewModel.getStep();
                if (step == TripFlowViewModel.Step.PICK_LOCATION
                        || step == TripFlowViewModel.Step.CONFIRM_PRICE) {
                    returnToIdle();
                } else if (step == TripFlowViewModel.Step.SEARCHING) {
                    searchingPanel.confirmCancel();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
    }

    /** El botón flotante superior izquierdo: menú en IDLE, atrás en el resto del flujo. */
    private void onNavButtonClicked() {
        if (viewModel.getStep() == TripFlowViewModel.Step.IDLE) {
            ((HomeActivity) requireActivity()).openDrawer();
        } else {
            backCallback.handleOnBackPressed();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Pasos del flujo
    // ---------------------------------------------------------------------------------------

    /**
     * Único punto donde el paso se traduce a UI. Todo lo que distingue un paso de otro —
     * qué panel se ve, qué tapa el mapa, si el modal se arrastra, qué hace el botón de atrás —
     * se decide aquí, en vez de repartido entre pantallas que no se conocían entre sí.
     *
     * @param animate false al pintar el paso inicial (no hay nada de lo que venir).
     */
    private void applyStep(TripFlowViewModel.Step step, boolean animate) {
        View root = getView();
        if (root == null) {
            return;
        }
        boolean idle = step == TripFlowViewModel.Step.IDLE;

        if (!idle) {
            exitSearchModeUi(root);
        }

        // El modal vuelve a su posición base antes de medir el panel nuevo: si venía expandido
        // (buscador abierto) el peek recién calculado no se aplicaría hasta soltarlo.
        sheetBehavior.setDraggable(idle);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        applyFullScreenTransition(0f);

        swapPanel(step, animate);

        // La tarjeta de ruta y el pin fijo son mutuamente excluyentes: uno muestra origen/destino
        // ya elegidos, el otro sirve para elegir uno. Nunca coinciden en el mismo paso.
        boolean hasRoute = step == TripFlowViewModel.Step.CONFIRM_PRICE
                || step == TripFlowViewModel.Step.SEARCHING;
        routeCard.setVisibility(hasRoute ? View.VISIBLE : View.GONE);
        // Con el viaje ya creado la ruta está cerrada: la parada solo se toca al negociar.
        View addStopRow = routeCard.findViewById(R.id.row_add_stop);
        addStopRow.setEnabled(step == TripFlowViewModel.Step.CONFIRM_PRICE);
        addStopRow.setClickable(step == TripFlowViewModel.Step.CONFIRM_PRICE);
        pickLocationPin.setVisibility(step == TripFlowViewModel.Step.PICK_LOCATION ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.btn_my_location).setVisibility(
                step == TripFlowViewModel.Step.SEARCHING ? View.GONE : View.VISIBLE);
        updateNavButtonIcon(root, idle);
        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) requireActivity()).setDrawerEnabled(idle);
        }
        mapPresenter.setGesturesEnabled(step != TripFlowViewModel.Step.SEARCHING);

        if (step != TripFlowViewModel.Step.SEARCHING) {
            searchingPanel.hide();
        }
        if (step != TripFlowViewModel.Step.PICK_LOCATION) {
            pickLocationPanel.hide();
        }
        if (idle) {
            mapPresenter.clearRoute();
        } else if (step == TripFlowViewModel.Step.PICK_LOCATION) {
            mapPresenter.clearRoute();
            pickLocationPanel.show();
        } else if (step == TripFlowViewModel.Step.CONFIRM_PRICE) {
            confirmPricePanel.show();
        } else {
            searchingPanel.show();
        }

        backCallback.setEnabled(!idle);
        // Refresca ya la zona útil del mapa por el cambio de la tarjeta de ruta; el alto del
        // modal lo ajusta swapPanel cuando el panel nuevo esté realmente puesto.
        updateSheetStops(root);
    }

    /**
     * Fundido del contenido del modal: se apaga, se cambia el panel visible y se vuelve a
     * encender. Se funde el contenedor entero y no un panel contra otro a propósito — si los dos
     * estuvieran visibles a la vez, aunque fuera 100ms, el modal mediría el alto del más grande
     * y daría un tirón antes de asentarse.
     */
    private void swapPanel(TripFlowViewModel.Step step, boolean animate) {
        Runnable swap = () -> {
            panelHome.setVisibility(step == TripFlowViewModel.Step.IDLE ? View.VISIBLE : View.GONE);
            panelPickLocation.setVisibility(
                    step == TripFlowViewModel.Step.PICK_LOCATION ? View.VISIBLE : View.GONE);
            panelConfirmPrice.setVisibility(
                    step == TripFlowViewModel.Step.CONFIRM_PRICE ? View.VISIBLE : View.GONE);
            panelSearching.setVisibility(
                    step == TripFlowViewModel.Step.SEARCHING ? View.VISIBLE : View.GONE);
            // A partir de aquí el modal ya mide otra cosa. No se remide en el acto: el cambio de
            // visibilidad agenda un pase de layout y el corte se recalcula ahí, con el alto real
            // del panel nuevo — medirlo antes daría el del panel que se acaba de ir.
            displayedStep = step;
            lastCollapsedHeightPx = -1;
            animateNextPeek = animate;
        };
        if (!animate) {
            swap.run();
            sheetContent.setAlpha(1f);
            return;
        }
        sheetContent.animate().cancel();
        sheetContent.animate().alpha(0f).setDuration(PANEL_FADE_OUT_MS).withEndAction(() -> {
            if (getView() == null) {
                return;
            }
            swap.run();
            sheetContent.animate().alpha(1f).setDuration(PANEL_FADE_IN_MS).start();
        }).start();
    }

    /**
     * Alterna el ícono del botón flotante entre menú y atrás con un fundido en el sitio. Solo
     * anima cuando el papel del botón cambia de verdad: entre CONFIRM_PRICE y SEARCHING sigue
     * siendo "atrás", y parpadear ahí sería ruido que además sugiere un cambio que no hubo.
     */
    private void updateNavButtonIcon(View root, boolean idle) {
        if (navButtonIdle != null && navButtonIdle == idle) {
            return;
        }
        boolean firstPaint = navButtonIdle == null;
        navButtonIdle = idle;
        ImageButton navButton = root.findViewById(R.id.btn_open_drawer);
        if (firstPaint) {
            applyNavIcon(navButton, idle);
            return;
        }
        navButton.animate().cancel();
        navButton.animate().alpha(0f).setDuration(NAV_ICON_FADE_MS).withEndAction(() -> {
            if (getView() == null) {
                return;
            }
            applyNavIcon(navButton, idle);
            navButton.animate().alpha(1f).setDuration(NAV_ICON_FADE_MS).start();
        }).start();
    }

    private void applyNavIcon(ImageButton navButton, boolean idle) {
        navButton.setImageResource(idle ? R.drawable.ic_menu : R.drawable.ic_back);
        navButton.setContentDescription(
                getString(idle ? R.string.nav_menu_description : R.string.action_back));
    }

    /** Destino elegido (buscador, dirección guardada o pin en el mapa): arranca la negociación. */
    private void startTripFlow(String destinationText, double lat, double lng) {
        viewModel.startDestination(originLocation, getString(R.string.home_origin_placeholder),
                new LatLng(lat, lng), destinationText);
        viewModel.goTo(TripFlowViewModel.Step.CONFIRM_PRICE);
    }

    /** Vuelta a Home descartando la solicitud en curso. */
    private void returnToIdle() {
        viewModel.clearTrip();
        viewModel.goTo(TripFlowViewModel.Step.IDLE);
    }

    /**
     * El viaje aceptado sigue siendo una Activity propia: es de larga duración y se reabre desde
     * una notificación, así que tiene entrada propia. Al lanzarla se limpia el flujo, para que al
     * volver de ella el home esté como recién abierto y no en el radar de un viaje ya cerrado.
     */
    private void goToActiveTrip(@NonNull Ride ride) {
        Intent intent = new Intent(requireContext(), ActiveTripActivity.class);
        intent.putExtra(ActiveTripActivity.EXTRA_RIDE_ID, viewModel.getRideId());
        intent.putExtra(ActiveTripActivity.EXTRA_DRIVER_INITIALS,
                SearchingPanel.initialsFor(ride.getDriverName()));
        intent.putExtra(ActiveTripActivity.EXTRA_DRIVER_NAME, ride.getDriverName());
        intent.putExtra(ActiveTripActivity.EXTRA_DRIVER_DETAILS, SearchingPanel.joinNonNull(" ",
                ride.getVehicleBrand(), ride.getVehicleModel(), ride.getVehicleColor()));
        intent.putExtra(ActiveTripActivity.EXTRA_PRICE,
                ride.getAgreedFare() != null ? ride.getAgreedFare().floatValue() : 0f);
        intent.putExtra(ActiveTripActivity.EXTRA_ORIGIN, viewModel.getOriginText());
        intent.putExtra(ActiveTripActivity.EXTRA_DESTINATION, viewModel.getDestinationText());
        LatLng origin = viewModel.getOrigin();
        LatLng destination = viewModel.getDestination();
        intent.putExtra(ActiveTripActivity.EXTRA_ORIGIN_LAT, origin != null ? origin.latitude : 0);
        intent.putExtra(ActiveTripActivity.EXTRA_ORIGIN_LNG, origin != null ? origin.longitude : 0);
        intent.putExtra(ActiveTripActivity.EXTRA_DESTINATION_LAT,
                destination != null ? destination.latitude : 0);
        intent.putExtra(ActiveTripActivity.EXTRA_DESTINATION_LNG,
                destination != null ? destination.longitude : 0);
        startActivity(intent);
        returnToIdle();
    }

    // ---------------------------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------------------------

    /**
     * Las suscripciones viven entre onStart y onStop, no entre onCreate y onDestroy: con la app
     * en segundo plano no hay nada que refrescar ni a quién mostrarle una oferta, y dejar los
     * callbacks registrados solo gastaría.
     */
    @Override
    public void onStart() {
        super.onStart();
        connectivitySubscription = connectivityRepository.observe(this::onConnectivityChanged);
        searchingPanel.onHostStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (connectivitySubscription != null) {
            connectivitySubscription.stop();
            connectivitySubscription = null;
        }
        reconnectedBannerHandler.removeCallbacksAndMessages(null);
        searchingPanel.onHostStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // El listener referencia vistas de esta vista, y el ViewModel sobrevive a ella.
        viewModel.setStepListener(null);
        searchingPanel.hide();
        mapPresenter.detach();
        searchDebounceHandler.removeCallbacksAndMessages(null);
        reconnectedBannerHandler.removeCallbacksAndMessages(null);
        googleMap = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Cubre volver de Ajustes tras otorgar el permiso manualmente: onViewCreated ya no
        // vuelve a correr (el patrón show/hide de HomeActivity no recrea este Fragment).
        if (hasLocationPermission() && googleMap != null && !googleMap.isMyLocationEnabled()) {
            showMyLocation();
        }
    }

    /**
     * Único punto donde se reacciona a la red. Antes, un fallo de carga era permanente: las
     * secciones se piden en onViewCreated y nada las volvía a pedir, así que quedarse sin
     * internet al abrir obligaba a cerrar y reabrir la app aunque la señal volviera.
     */
    private void onConnectivityChanged(boolean online) {
        View root = getView();
        if (root == null) {
            return;
        }
        showOfflineBanner(!online);
        if (!online) {
            wasOffline = true;
            return;
        }
        if (wasOffline) {
            wasOffline = false;
            showReconnectedBanner();
        }
        if (!greetingLoaded) {
            loadGreeting(root);
        }
        if (!savedAddressesLoaded) {
            loadSavedAddresses(root);
        }
        if (!recentTripsLoaded) {
            loadRecentTrips(root);
        }
    }

    private void showOfflineBanner(boolean show) {
        if (offlineBanner == null || (offlineBanner.getVisibility() == View.VISIBLE) == show) {
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

    /**
     * A diferencia del banner rojo (que se queda mientras dure el problema), este se retira
     * solo: confirma la reconexión y no necesita quedarse ahí una vez que el usuario ya lo vio.
     */
    private void showReconnectedBanner() {
        if (onlineBanner == null) {
            return;
        }
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

    // ---------------------------------------------------------------------------------------
    // Modal y medidas
    // ---------------------------------------------------------------------------------------

    private static final int PEEK_ADDRESS_ROWS = 3;
    /** Igual que el layout_margin de btn_open_drawer / btn_my_location en fragment_home.xml. */
    private static final int FLOATING_BUTTON_MARGIN_DP = 16;
    /** Justo debajo de la fila de botones flotantes: 16dp de margen + 44dp de alto + 8dp de aire. */
    private static final int ROUTE_CARD_TOP_MARGIN_DP = 68;
    /** Aire entre la tarjeta de ruta y el borde superior de la zona útil del mapa. */
    private static final int ROUTE_CARD_BOTTOM_GAP_DP = 8;
    /** Igual que el radio de bg_sheet_top_rounded.xml — desde ahí se anima hasta 0. */
    private static final int SHEET_CORNER_RADIUS_DP = 24;
    /**
     * Fracción del recorrido a partir de la cual el modal empieza a "pegarse" al tope: se tapa
     * la status bar y se pierden las esquinas redondeadas. Arranca tarde a propósito — hacerlo
     * de forma lineal desde el estado colapsado teñiría la status bar con el mapa aún medio
     * visible, que se ve raro.
     */
    private static final float SHEET_FULLSCREEN_FADE_START = 0.85f;

    /**
     * El listener NO se auto-remueve: el contenido del modal cambia solo (direcciones guardadas
     * llegan async, el panel de tarifa crece al llegar la cotización), así que el corte se
     * recalcula en cada pase de layout y no una única vez con la pantalla a medio llenar.
     *
     * <p>Basta con registrarlo en una vista: el ViewTreeObserver es de la ventana entera, así que
     * un cambio en cualquier descendiente lo dispara igual.
     */
    private void setUpBottomSheet(View root) {
        View sheet = root.findViewById(R.id.sheet_container);
        sheetBehavior = BottomSheetBehavior.from(sheet);
        sheetBehavior.setHideable(false);
        sheetBehavior.setSkipCollapsed(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        ViewTreeObserver.OnGlobalLayoutListener layoutListener = () -> updateSheetStops(root);
        sheetContent.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);

        statusBarScrim = root.findViewById(R.id.scrim_status_bar);
        sheetCornerRadiusPx = SHEET_CORNER_RADIUS_DP * getResources().getDisplayMetrics().density;
        // mutate() para no compartir el estado del drawable con cualquier otra vista que use
        // bg_sheet_top_rounded: aquí se le cambia el radio en caliente.
        if (sheet.getBackground() instanceof GradientDrawable) {
            sheetBackground = (GradientDrawable) sheet.getBackground().mutate();
            sheet.setBackground(sheetBackground);
        }

        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                // Los estados finales se fijan aquí y no solo en onSlide: en una transición
                // programática (setState) onSlide no siempre llega a reportar el 0 ó el 1
                // exactos, y el velo se quedaría a medio camino.
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    applyFullScreenTransition(0f);
                    // Cubre que el usuario arrastre el modal hacia abajo mientras busca, en vez
                    // de usar el botón atrás — ver setUpDestinationSearch.
                    if (searchModeActive) {
                        exitSearchModeUi(root);
                    }
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    applyFullScreenTransition(1f);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                applyFullScreenTransition(slideOffset);
            }
        });
    }

    /**
     * Funde la status bar con el modal conforme éste llega al tope: tiñe el velo superior del
     * color de la superficie y quita el redondeo de las esquinas, de modo que expandido parezca
     * pantalla completa. {@code slideOffset} va de 0 (colapsado) a 1 (expandido).
     */
    private void applyFullScreenTransition(float slideOffset) {
        float progress = (slideOffset - SHEET_FULLSCREEN_FADE_START) / (1f - SHEET_FULLSCREEN_FADE_START);
        progress = Math.max(0f, Math.min(1f, progress));

        statusBarScrim.setAlpha(progress);
        if (sheetBackground == null) {
            return;
        }
        float radius = sheetCornerRadiusPx * (1f - progress);
        // Solo las dos esquinas de arriba, igual que bg_sheet_top_rounded.
        sheetCornerRadii[0] = radius;
        sheetCornerRadii[1] = radius;
        sheetCornerRadii[2] = radius;
        sheetCornerRadii[3] = radius;
        sheetBackground.setCornerRadii(sheetCornerRadii);
    }

    /**
     * Insets superiores explícitos: el modal topa justo debajo de la status bar (nunca detrás),
     * y los botones flotantes y la tarjeta de ruta se bajan para no quedar bajo ella.
     *
     * <p>Esto es lo que mantiene el borde superior del modal idéntico en todos los estados. El
     * CoordinatorLayout tenía {@code fitsSystemWindows="true"}, y eso le metía al sheet el
     * inset de la status bar como paddingTop solo al expandirse — medido en un SM-A165M: 0px
     * colapsado → 100px expandido. Ese salto era el "borde que crece" al abrir el teclado, y
     * quedaba visible al volver a colapsar. Gestionando el inset aquí, la geometría interna del
     * modal es constante y el mapa queda a pantalla completa por debajo.
     *
     * <p>Se leen solo {@code systemBars + displayCutout}: el inset del teclado
     * ({@code Type.ime()}) queda deliberadamente fuera, para que abrirlo o cerrarlo no pueda
     * mover nada de esto.
     */
    private void applyTopInsets(View root) {
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

        // Altura explícita en vez de un margen superior: con margen, la posición que deja un
        // onLayoutChild recién hecho difiere en esos mismos px de la que deja el asentamiento
        // normal del behavior, y el modal daba un salto en el primer arrastre. Acotando la
        // altura se consigue el mismo tope (fitToContentsOffset = alto del padre - alto del
        // hijo = inset) sin margen que descuadre ese cálculo.
        View sheet = root.findViewById(R.id.sheet_container);
        int availableHeightPx = root.getHeight() - topInsetPx;
        ViewGroup.LayoutParams sheetParams = sheet.getLayoutParams();
        if (root.getHeight() > 0 && sheetParams.height != availableHeightPx) {
            sheetParams.height = availableHeightPx;
            sheet.setLayoutParams(sheetParams);
        }

        float density = getResources().getDisplayMetrics().density;
        int buttonMarginPx = Math.round(FLOATING_BUTTON_MARGIN_DP * density);
        setTopMargin(root.findViewById(R.id.btn_open_drawer), topInsetPx + buttonMarginPx);
        setTopMargin(root.findViewById(R.id.btn_my_location), topInsetPx + buttonMarginPx);
        // Mismo margen que los botones (y mismo alto en el layout): así los tres quedan
        // centrados en la misma fila, no la píldora flotando por encima de ellos.
        setTopMargin(root.findViewById(R.id.banner_offline), topInsetPx + buttonMarginPx);
        setTopMargin(root.findViewById(R.id.banner_online), topInsetPx + buttonMarginPx);
        setTopMargin(routeCard, topInsetPx + Math.round(ROUTE_CARD_TOP_MARGIN_DP * density));

        // El velo cubre exactamente la franja que el modal deja libre al toparse abajo de ella.
        ViewGroup.LayoutParams scrimParams = statusBarScrim.getLayoutParams();
        if (scrimParams.height != topInsetPx) {
            scrimParams.height = topInsetPx;
            statusBarScrim.setLayoutParams(scrimParams);
        }
    }

    private void setTopMargin(View view, int topMarginPx) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.topMargin != topMarginPx) {
            params.topMargin = topMarginPx;
            view.setLayoutParams(params);
        }
    }

    /**
     * Recalcula dónde corta el modal y, con ese número, cuánta pantalla le queda libre al mapa.
     * En IDLE el corte se mide a partir del contenido (saludo + buscador + hasta
     * {@link #PEEK_ADDRESS_ROWS} direcciones) para que caiga bien haya las que haya; en los demás
     * pasos el modal se ajusta al alto exacto de su panel.
     */
    private void updateSheetStops(View root) {
        applyTopInsets(root);
        int collapsedHeightPx = displayedStep == TripFlowViewModel.Step.IDLE
                ? idlePeekHeight(root) : sheetContent.getHeight();
        if (collapsedHeightPx <= 0) {
            return;
        }

        // El listener se dispara en CUALQUIER pase de layout, no solo cuando cambia el
        // contenido. Sin este memo, setPeekHeight() se repetiría en cada uno.
        if (collapsedHeightPx != lastCollapsedHeightPx) {
            lastCollapsedHeightPx = collapsedHeightPx;
            sheetBehavior.setPeekHeight(collapsedHeightPx, animateNextPeek);
            animateNextPeek = false;
        }
        // Fuera del memo: la tarjeta de ruta aparece con alto 0 y solo queda medida en el pase
        // siguiente, cuando el modal ya no cambió de alto y el memo cortaría la actualización.
        updateMapViewport(collapsedHeightPx);
    }

    private int idlePeekHeight(View root) {
        View peekContent = root.findViewById(R.id.group_peek_content);
        int peekHeightPx = peekContent.getHeight();
        if (peekHeightPx <= 0) {
            return 0;
        }
        View addressLabel = root.findViewById(R.id.text_saved_addresses_label);
        LinearLayout addressContainer = root.findViewById(R.id.container_saved_addresses);
        return peekHeightPx + addressLabel.getHeight()
                + heightOfFirstRows(addressContainer, PEEK_ADDRESS_ROWS);
    }

    /**
     * Le dice al mapa cuánto le tapan la tarjeta de ruta y el modal, y centra el radar en lo que
     * queda. Aquí es donde deja de hacer falta ajustar márgenes a mano en cada pantalla: el
     * encuadre de la ruta lo resuelve el SDK contra esta zona útil, no contra la pantalla entera.
     */
    private void updateMapViewport(int sheetHeightPx) {
        int topPaddingPx = sheetTopInsetPx;
        if (routeCard.getVisibility() == View.VISIBLE && routeCard.getHeight() > 0) {
            topPaddingPx = routeCard.getBottom()
                    + Math.round(ROUTE_CARD_BOTTOM_GAP_DP * getResources().getDisplayMetrics().density);
        }
        mapPresenter.setContentPadding(topPaddingPx, sheetHeightPx);

        // Centrado en el hueco visible, no en la pantalla: en un CoordinatorLayout la gravedad
        // "center" se aplica sobre el rectángulo del padre ya recortado por los márgenes. El pin
        // de PICK_LOCATION comparte el mismo cálculo — nunca están visibles a la vez, pero no
        // cuesta nada mantener los dos al día nada más pedirlo el mapa.
        centerInViewport(radarOverlay, topPaddingPx, sheetHeightPx);
        centerInViewport(pickLocationPin, topPaddingPx, sheetHeightPx);
    }

    private void centerInViewport(View overlay, int topPaddingPx, int bottomPaddingPx) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) overlay.getLayoutParams();
        if (params.topMargin != topPaddingPx || params.bottomMargin != bottomPaddingPx) {
            params.topMargin = topPaddingPx;
            params.bottomMargin = bottomPaddingPx;
            overlay.setLayoutParams(params);
        }
    }

    /** container arma sus hijos como fila, separador, fila, separador... (bindSavedAddresses) —
     * los índices pares son filas, así que se cuentan como tales; los impares (separadores) se
     * suman igual cuando caen entre dos filas contadas. */
    private int heightOfFirstRows(LinearLayout container, int rowCount) {
        int totalHeightPx = 0;
        int rowsCounted = 0;
        for (int i = 0; i < container.getChildCount() && rowsCounted < rowCount; i++) {
            View child = container.getChildAt(i);
            totalHeightPx += child.getHeight();
            if (i % 2 == 0) {
                rowsCounted++;
            }
        }
        return totalHeightPx;
    }

    // ---------------------------------------------------------------------------------------
    // Paso IDLE: buscador y listas
    // ---------------------------------------------------------------------------------------

    private void setUpDestinationSearch(View root) {
        EditText input = root.findViewById(R.id.input_destination);
        ImageView leadingIcon = root.findViewById(R.id.icon_search_leading);
        ImageView clearButton = root.findViewById(R.id.btn_clear_destination);

        // input_destination vive dentro de un NestedScrollView que a la vez es el target de
        // un BottomSheetBehavior: ambos interceptan el primer toque en ACTION_DOWN para decidir
        // si es scroll/drag antes de dejarlo llegar al hijo (el clásico bug de "EditText dentro
        // de ScrollView necesita 2 toques"). Avisar aquí que no intercepten evita que el primer
        // toque se pierda.
        input.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                enterSearchMode(root);
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                scheduleSearch(root, s.toString());
            }
        });

        // Siempre lupa, sin flecha "atrás": para salir de la búsqueda ya está el arrastre del
        // modal y el botón atrás del sistema (ver backCallback) — un tercer affordance
        // redundante en el propio ícono solo añadía confusión.
        leadingIcon.setOnClickListener(v -> input.requestFocus());

        clearButton.setOnClickListener(v -> input.setText(""));

        root.findViewById(R.id.row_pick_on_map).setOnClickListener(v -> {
            cancelSearch(root);
            viewModel.goTo(TripFlowViewModel.Step.PICK_LOCATION);
        });
    }

    private void enterSearchMode(View root) {
        if (searchModeActive || viewModel.getStep() != TripFlowViewModel.Step.IDLE) {
            return;
        }
        searchModeActive = true;
        backCallback.setEnabled(true);
        // Síncrono a propósito (no root.post): diferirlo abría una carrera real — si el
        // usuario cancelaba la búsqueda antes de que el Runnable pospuesto llegara a
        // ejecutarse, éste corría de todos modos DESPUÉS del cancelSearch y volvía a expandir
        // el sheet (reinflando el spacer superior) justo después de haberlo cerrado. El input
        // ya no necesita este diferido: el doble-toque se resolvió con
        // requestDisallowInterceptTouchEvent arriba, no con esto. El sheet se deja arrastrable
        // (default): el usuario puede bajarlo con el dedo aunque esté buscando, sin depender
        // solo del botón atrás — ver onStateChanged en setUpBottomSheet.
        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    /** Acción explícita del usuario (botón atrás del sistema, elegir un destino): limpia y colapsa. */
    private void cancelSearch(View root) {
        exitSearchModeUi(root);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    /**
     * Limpia el estado de búsqueda sin tocar el BottomSheetBehavior — se usa tanto desde
     * {@link #cancelSearch} como cuando el propio usuario arrastra el modal hasta colapsarlo
     * (ver onStateChanged en setUpBottomSheet), caso en el que el sheet ya está colapsado y
     * llamar a setState de nuevo sería redundante.
     */
    private void exitSearchModeUi(View root) {
        if (!searchModeActive) {
            return;
        }
        searchDebounceHandler.removeCallbacksAndMessages(null);
        EditText input = root.findViewById(R.id.input_destination);
        input.setText("");
        input.clearFocus();

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }

        searchModeActive = false;
        backCallback.setEnabled(viewModel.getStep() != TripFlowViewModel.Step.IDLE);
    }

    private void scheduleSearch(View root, String query) {
        searchDebounceHandler.removeCallbacksAndMessages(null);
        if (query.trim().isEmpty()) {
            showPredictions(root, Collections.emptyList());
            return;
        }
        pendingSearchRunnable = () -> placesAutocompleteService.findPredictions(
                requireContext(), query, originLocation, predictions -> {
                    if (isAdded()) {
                        showPredictions(root, predictions);
                    }
                });
        searchDebounceHandler.postDelayed(pendingSearchRunnable, SEARCH_DEBOUNCE_MS);
    }

    private void showPredictions(View root, List<AutocompletePrediction> predictions) {
        EditText input = root.findViewById(R.id.input_destination);
        boolean hasQuery = input.getText().length() > 0;

        root.findViewById(R.id.group_default_lists).setVisibility(hasQuery ? View.GONE : View.VISIBLE);
        View predictionsContainer = root.findViewById(R.id.container_predictions);
        predictionsContainer.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
        if (!hasQuery) {
            return;
        }
        bindPredictions((LinearLayout) predictionsContainer, predictions);
    }

    private void bindPredictions(LinearLayout container, List<AutocompletePrediction> predictions) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (AutocompletePrediction prediction : predictions) {
            View row = inflater.inflate(R.layout.item_place_prediction, container, false);
            ((TextView) row.findViewById(R.id.text_prediction_primary)).setText(prediction.getPrimaryText(null));
            ((TextView) row.findViewById(R.id.text_prediction_secondary)).setText(prediction.getSecondaryText(null));
            row.setOnClickListener(v -> placesAutocompleteService.resolvePlace(
                    requireContext(), prediction.getPlaceId(), new PlacesAutocompleteService.ResultListener() {
                        @Override
                        public void onPlaceSelected(String address, double lat, double lng) {
                            if (!isAdded()) {
                                return;
                            }
                            cancelSearch(requireView());
                            startTripFlow(address, lat, lng);
                        }
                    }));
            container.addView(row);
        }
    }

    private void loadGreeting(View root) {
        UserRepository userRepository = new RestUserRepository(requireContext());
        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded()) {
                    return;
                }
                greetingLoaded = true;
                String firstName = profile.getName() != null ? profile.getName().split("\\s+")[0] : "";
                ((TextView) root.findViewById(R.id.text_greeting)).setText(getString(R.string.home_greeting, firstName));
            }

            @Override
            public void onError(ApiException error) {
                // El saludo es cosmético; sin nombre solo se deja el placeholder del layout. Pero
                // sí queda pendiente, para recuperarlo al volver la señal.
                greetingLoaded = false;
            }
        });
    }

    private void loadSavedAddresses(View root) {
        addressRepository.getAll(new ApiCallback<List<SavedAddress>>() {
            @Override
            public void onSuccess(List<SavedAddress> addresses) {
                savedAddressesLoaded = true;
                if (isAdded()) {
                    bindSavedAddresses(root, addresses);
                }
            }

            @Override
            public void onError(ApiException error) {
                // Se pinta la lista vacía para que el modal no quede a medias, pero marcada como
                // pendiente: sin esto, "no hay direcciones" y "no hubo red" se veían igual y ya
                // nunca se distinguían.
                savedAddressesLoaded = false;
                if (isAdded()) {
                    bindSavedAddresses(root, Collections.emptyList());
                }
            }
        });
    }

    private void bindSavedAddresses(View root, List<SavedAddress> addresses) {
        LinearLayout container = root.findViewById(R.id.container_saved_addresses);
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < addresses.size(); i++) {
            SavedAddress address = addresses.get(i);
            View row = inflater.inflate(R.layout.item_saved_address, container, false);

            AddressLabel icon = AddressLabel.fromText(requireContext(), address.getLabel());
            ((TextView) row.findViewById(R.id.text_address_emoji)).setText(icon.getEmoji());
            ((TextView) row.findViewById(R.id.text_address_label)).setText(address.getLabel());
            ((TextView) row.findViewById(R.id.text_address_line)).setText(address.getAddress());

            row.setOnClickListener(v -> startTripFlow(address.getAddress(), address.getLat(), address.getLng()));
            container.addView(row);

            if (i < addresses.size() - 1) {
                container.addView(buildDivider());
            }
        }
    }

    private static final int RECENT_TRIPS_LIMIT = 3;

    private void loadRecentTrips(View root) {
        new RestTripRepository(requireContext()).getRideHistory(RECENT_TRIPS_LIMIT,
                new ApiCallback<List<RideSummary>>() {
                    @Override
                    public void onSuccess(List<RideSummary> rides) {
                        recentTripsLoaded = true;
                        if (isAdded()) {
                            bindRecentTrips(root, rides);
                        }
                    }

                    @Override
                    public void onError(ApiException error) {
                        // Sección secundaria del modal; sin viajes recientes simplemente queda
                        // vacía, pero pendiente de reintento al volver la señal.
                        recentTripsLoaded = false;
                    }
                });
    }

    private void bindRecentTrips(View root, List<RideSummary> rides) {
        LinearLayout container = root.findViewById(R.id.container_recent_trips);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (RideSummary ride : rides) {
            View row = TripHistoryRowBinder.addTrip(inflater, container, ride);
            row.setOnClickListener(v -> TripDetailBottomSheet.present(getChildFragmentManager(), ride.getId()));
        }
    }

    private View buildDivider() {
        View divider = new View(requireContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(
                ColorUtils.resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOutline));
        return divider;
    }

    // ---------------------------------------------------------------------------------------
    // Mapa y ubicación
    // ---------------------------------------------------------------------------------------

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(requireContext(), googleMap);

        // Arrancar en la última posición vista (si existe) en vez de CDMX por defecto: evita
        // el salto visual al llegar la ubicación real, y esa zona ya suele tener tiles en
        // caché de la sesión anterior.
        CameraPosition initialCamera = readCachedCameraPosition();
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                initialCamera != null ? initialCamera : CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 15f)));
        // El SDK solo permite un listener de cada tipo por mapa: como es compartido entre pasos,
        // este es el único punto que los registra, y delega a PickLocationPanel según si su
        // paso está activo (ver su javadoc) en vez de pelear por el mismo slot de listener.
        googleMap.setOnCameraMoveStartedListener(reason -> pickLocationPanel.onCameraMoveStarted());
        googleMap.setOnCameraIdleListener(() -> {
            saveCameraPosition();
            pickLocationPanel.onCameraIdle();
        });

        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mapPresenter.attach(googleMap, mapFragment != null ? mapFragment.getView() : null);
        mapPresenter.setGesturesEnabled(viewModel.getStep() != TripFlowViewModel.Step.SEARCHING);
        // Volver a este paso con el mapa recién listo (rotación a mitad del flujo): la ruta la
        // dibuja el panel al mostrarse, pero el mapa aún no existía cuando eso pasó.
        if (viewModel.getStep() != TripFlowViewModel.Step.IDLE) {
            mapPresenter.showRoute(viewModel.getRoutePoints());
        }

        if (hasLocationPermission()) {
            showMyLocation();
        }
    }

    private CameraPosition readCachedCameraPosition() {
        String raw = new PrefsHelper(requireContext()).getString(PREF_KEY_CAMERA, null);
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            LatLng target = new LatLng(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
            return CameraPosition.fromLatLngZoom(target, Float.parseFloat(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveCameraPosition() {
        if (googleMap == null) {
            return;
        }
        CameraPosition position = googleMap.getCameraPosition();
        String raw = position.target.latitude + "," + position.target.longitude + "," + position.zoom;
        new PrefsHelper(requireContext()).putString(PREF_KEY_CAMERA, raw);
    }

    private void onPermissionResult(Map<String, Boolean> grantedPermissions) {
        boolean granted = Boolean.TRUE.equals(grantedPermissions.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(grantedPermissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));
        if (granted) {
            showMyLocation();
        } else if (getView() != null) {
            showPermissionDeniedSnackbar();
        }
        // Se encadena aquí (y no antes) para que nunca haya dos requestPermissions() pendientes
        // a la vez — ver el comentario en HomeActivity.requestNotificationPermissionAndRegisterToken().
        requestNotificationPermission();
    }

    private void showPermissionDeniedSnackbar() {
        Snackbar snackbar = Snackbar.make(getView(), R.string.home_permission_rationale, Snackbar.LENGTH_LONG);
        boolean canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION);
        if (canAskAgain) {
            snackbar.setAction(R.string.home_permission_retry, v -> permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }));
        } else {
            snackbar.setAction(R.string.home_permission_open_settings, v -> openAppSettings());
        }
        snackbar.show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }

    private void requestNotificationPermission() {
        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) requireActivity()).requestNotificationPermissionAndRegisterToken();
        }
    }

    @SuppressLint("MissingPermission")
    private void showMyLocation() {
        if (googleMap == null) {
            return;
        }
        googleMap.setMyLocationEnabled(true);
        // El botón redondo propio (btn_my_location) reemplaza al del SDK para que comparta
        // estilo con el de menú — el del SDK se queda apagado a propósito.
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null || googleMap == null) {
                return;
            }
            originLocation = new LatLng(location.getLatitude(), location.getLongitude());
            // Con un viaje en curso la cámara la manda la ruta, no la ubicación: recentrar aquí
            // desharía el encuadre justo después de haberlo hecho.
            if (viewModel.getStep() == TripFlowViewModel.Step.IDLE) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(originLocation, 16f));
            }
        });
    }

    private boolean hasLocationPermission() {
        boolean fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return fine || coarse;
    }
}
