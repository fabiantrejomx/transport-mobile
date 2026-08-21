package com.bng.drivo.ui.home;

import android.Manifest;
import android.app.Activity;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.bng.drivo.R;
import com.bng.drivo.data.model.AddressLabel;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AddressRepository;
import com.bng.drivo.data.repository.RestAddressRepository;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.service.PlacesAutocompleteService;
import com.bng.drivo.ui.destination.PickLocationOnMapActivity;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.ui.price.ConfirmPriceActivity;
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
 * Pestaña Home del pasajero: mapa a pantalla completa + modal persistente tipo iOS con el
 * saludo, buscador de destino, "elegir en el mapa", direcciones guardadas y últimos viajes —
 * absorbe lo que antes era la pantalla separada SetDestinationActivity (ver el plan de
 * rediseño). El modal nunca se puede ocultar del todo: su tope de colapso corta justo después
 * de las direcciones guardadas ({@code group_peek_content}, medido en tiempo real).
 */
public class HomeFragment extends Fragment implements OnMapReadyCallback {

    // Ciudad de México como origen por defecto, antes de obtener la ubicación real.
    private static final LatLng DEFAULT_POSITION = new LatLng(19.4326, -99.1332);
    private static final String PREF_KEY_CAMERA = "home_camera_position";
    private static final long SEARCH_DEBOUNCE_MS = 300;

    private FusedLocationProviderClient fusedLocationClient;
    private GoogleMap googleMap;
    private BottomSheetBehavior<View> sheetBehavior;
    private LatLng originLocation = DEFAULT_POSITION;

    private AddressRepository addressRepository;
    private final PlacesAutocompleteService placesAutocompleteService = new PlacesAutocompleteService(this);
    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchRunnable;
    private boolean searchModeActive;
    private OnBackPressedCallback searchBackCallback;
    private int lastCollapsedHeightPx = -1;
    private int sheetTopInsetPx;
    private View statusBarScrim;
    @Nullable
    private GradientDrawable sheetBackground;
    private float sheetCornerRadiusPx;
    /** Reutilizado en cada frame de onSlide para no asignar un array por fotograma. */
    private final float[] sheetCornerRadii = new float[8];

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);
    private final ActivityResultLauncher<Intent> pickLocationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onLocationPicked);

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

        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        searchBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                cancelSearch(view);
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), searchBackCallback);

        setUpBottomSheet(view);
        setUpDestinationSearch(view);
        loadGreeting(view);
        loadSavedAddresses(view);
        loadRecentTrips(view);

        view.findViewById(R.id.btn_open_drawer).setOnClickListener(v ->
                ((HomeActivity) requireActivity()).openDrawer());
        view.findViewById(R.id.btn_my_location).setOnClickListener(v -> {
            if (hasLocationPermission()) {
                showMyLocation();
            } else {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });

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

    @Override
    public void onResume() {
        super.onResume();
        // Cubre volver de Ajustes tras otorgar el permiso manualmente: onViewCreated ya no
        // vuelve a correr (el patrón show/hide de HomeActivity no recrea este Fragment).
        if (hasLocationPermission() && googleMap != null && !googleMap.isMyLocationEnabled()) {
            showMyLocation();
        }
    }

    private static final int PEEK_ADDRESS_ROWS = 3;
    /** Igual que el layout_margin de btn_open_drawer / btn_my_location en fragment_home.xml. */
    private static final int FLOATING_BUTTON_MARGIN_DP = 16;
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
     * 2 niveles: colapsado (saludo + buscador + elegir-en-mapa + hasta las últimas
     * {@link #PEEK_ADDRESS_ROWS} direcciones guardadas) y expandido (todo, hasta el tope). Se
     * mide en tiempo real a partir del contenido en vez de un dp fijo adivinado, para que corte
     * justo ahí sin importar cuántas direcciones haya.
     *
     * <p>Los listeners NO se auto-remueven: direcciones guardadas llega async
     * (loadSavedAddresses), así que el primer layout mide el contenido antes de que esas filas
     * existan — si se removieran tras la primera medición, el corte quedaba fijo en el tamaño de
     * la pantalla vacía.
     */
    private void setUpBottomSheet(View root) {
        View sheet = root.findViewById(R.id.sheet_container);
        sheetBehavior = BottomSheetBehavior.from(sheet);
        sheetBehavior.setHideable(false);
        sheetBehavior.setSkipCollapsed(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        View peekContent = root.findViewById(R.id.group_peek_content);
        peekContent.getViewTreeObserver().addOnGlobalLayoutListener(() -> updateSheetStops(root));
        LinearLayout addressContainer = root.findViewById(R.id.container_saved_addresses);
        addressContainer.getViewTreeObserver().addOnGlobalLayoutListener(() -> updateSheetStops(root));

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
     * y los botones flotantes se bajan para no quedar bajo ella.
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

        int buttonMarginPx = Math.round(FLOATING_BUTTON_MARGIN_DP * getResources().getDisplayMetrics().density);
        setTopMargin(root.findViewById(R.id.btn_open_drawer), topInsetPx + buttonMarginPx);
        setTopMargin(root.findViewById(R.id.btn_my_location), topInsetPx + buttonMarginPx);

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

    private void updateSheetStops(View root) {
        applyTopInsets(root);
        View peekContent = root.findViewById(R.id.group_peek_content);
        int peekHeightPx = peekContent.getHeight();
        if (peekHeightPx <= 0) {
            return;
        }

        View addressLabel = root.findViewById(R.id.text_saved_addresses_label);
        LinearLayout addressContainer = root.findViewById(R.id.container_saved_addresses);
        int collapsedHeightPx = peekHeightPx + addressLabel.getHeight()
                + heightOfFirstRows(addressContainer, PEEK_ADDRESS_ROWS);

        // El listener de arriba se dispara en CUALQUIER pase de layout, no solo cuando cambia el
        // contenido. Sin este memo, setPeekHeight() se repetiría en cada uno.
        if (collapsedHeightPx == lastCollapsedHeightPx) {
            return;
        }
        lastCollapsedHeightPx = collapsedHeightPx;

        sheetBehavior.setPeekHeight(collapsedHeightPx);
        if (googleMap != null) {
            googleMap.setPadding(0, sheetTopInsetPx, 0, collapsedHeightPx);
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
        // modal y el botón atrás del sistema (ver searchBackCallback) — un tercer affordance
        // redundante en el propio ícono solo añadía confusión.
        leadingIcon.setOnClickListener(v -> input.requestFocus());

        clearButton.setOnClickListener(v -> input.setText(""));

        root.findViewById(R.id.row_pick_on_map).setOnClickListener(v -> {
            cancelSearch(root);
            pickLocationLauncher.launch(new Intent(requireContext(), PickLocationOnMapActivity.class));
        });
    }

    private void enterSearchMode(View root) {
        if (searchModeActive) {
            return;
        }
        searchModeActive = true;
        searchBackCallback.setEnabled(true);
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

    /** Acción explícita del usuario (flecha, botón atrás del sistema): limpia y colapsa. */
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
        searchBackCallback.setEnabled(false);
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
                            goToConfirmPrice(address, lat, lng);
                        }
                    }));
            container.addView(row);
        }
    }

    private void onLocationPicked(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        String address = result.getData().getStringExtra(PickLocationOnMapActivity.EXTRA_ADDRESS);
        double lat = result.getData().getDoubleExtra(PickLocationOnMapActivity.EXTRA_LAT, 0);
        double lng = result.getData().getDoubleExtra(PickLocationOnMapActivity.EXTRA_LNG, 0);
        goToConfirmPrice(address, lat, lng);
    }

    private void goToConfirmPrice(String destinationText, double lat, double lng) {
        Intent intent = new Intent(requireContext(), ConfirmPriceActivity.class);
        intent.putExtra(ConfirmPriceActivity.EXTRA_ORIGIN, getString(R.string.home_origin_placeholder));
        intent.putExtra(ConfirmPriceActivity.EXTRA_DESTINATION, destinationText);
        intent.putExtra(ConfirmPriceActivity.EXTRA_ORIGIN_LAT, originLocation.latitude);
        intent.putExtra(ConfirmPriceActivity.EXTRA_ORIGIN_LNG, originLocation.longitude);
        intent.putExtra(ConfirmPriceActivity.EXTRA_DESTINATION_LAT, lat);
        intent.putExtra(ConfirmPriceActivity.EXTRA_DESTINATION_LNG, lng);
        startActivity(intent);
    }

    private void loadGreeting(View root) {
        UserRepository userRepository = new RestUserRepository(requireContext());
        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded()) {
                    return;
                }
                String firstName = profile.getName() != null ? profile.getName().split("\\s+")[0] : "";
                ((TextView) root.findViewById(R.id.text_greeting)).setText(getString(R.string.home_greeting, firstName));
            }

            @Override
            public void onError(ApiException error) {
                // El saludo es cosmético; sin nombre solo se deja el placeholder del layout.
            }
        });
    }

    private void loadSavedAddresses(View root) {
        addressRepository.getAll(new ApiCallback<List<SavedAddress>>() {
            @Override
            public void onSuccess(List<SavedAddress> addresses) {
                if (isAdded()) {
                    bindSavedAddresses(root, addresses);
                }
            }

            @Override
            public void onError(ApiException error) {
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

            row.setOnClickListener(v -> goToConfirmPrice(address.getAddress(), address.getLat(), address.getLng()));
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
                        if (isAdded()) {
                            bindRecentTrips(root, rides);
                        }
                    }

                    @Override
                    public void onError(ApiException error) {
                        // Sección secundaria del modal; sin viajes recientes simplemente queda vacía.
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
        googleMap.setOnCameraIdleListener(this::saveCameraPosition);

        if (sheetBehavior != null) {
            googleMap.setPadding(0, 0, 0, sheetBehavior.getPeekHeight());
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
            if (location != null) {
                originLocation = new LatLng(location.getLatitude(), location.getLongitude());
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
