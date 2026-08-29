package com.bng.drivo.ui.address;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bng.drivo.R;
import com.bng.drivo.data.model.AddressLabel;
import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AddressRepository;
import com.bng.drivo.data.repository.RestAddressRepository;
import com.bng.drivo.service.PlacesAutocompleteService;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.util.GeocoderHelper;
import com.bng.drivo.util.LoadingButtonHelper;
import com.bng.drivo.util.ValidationHelper;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Collections;
import java.util.List;

/**
 * Alta y edición de una dirección guardada. Es una Activity aparte a propósito —guardar sitios no
 * tiene nada que ver con pedir un viaje—, pero el mapa y el modal se comportan igual que en el
 * resto del rediseño: mapa a pantalla completa, dos flotantes redondos arriba y un modal que sube
 * a pantalla completa cuando hay teclado, tiñendo la status bar.
 *
 * <p><b>De dónde sale la dirección.</b> No se teclea: la fija el pin del mapa (geocodificación
 * inversa al soltarlo) o la sugerencia que se elija en el buscador, y la pantalla la enseña como
 * texto, no como campo. Antes era un {@code EditText} editable, y eso prometía una edición que no
 * existía —el primer arrastre del mapa la sobreescribía— además de cortar las direcciones largas
 * en una sola línea.
 *
 * <p><b>Cómo se llama una dirección.</b> El contrato guarda {@code label} como texto libre, así
 * que los tres chips no son un enum del servidor: "Casa" y "Trabajo" son nombres reservados —solo
 * puede haber uno de cada, y el chip se bloquea si ya existe—, mientras que "Otro" abre un campo
 * para que el usuario le ponga el nombre que quiera ("Gimnasio", "Casa de mamá"). Al editar una
 * dirección Casa/Trabajo ya creada no se ofrece cambiarle el nombre: se cambia la dirección y ya.
 *
 * <p>La unicidad de esos nombres la impone el servidor, no esta pantalla: apagar el chip ocupado
 * es una cortesía para no dejar intentar lo que va a fallar, pero quien decide es el índice único
 * de {@code passenger_favorites}. Si aun así choca —dos teléfonos a la vez, un nombre libre que
 * dejó de estarlo— llega {@code FAVORITE_LABEL_TAKEN} y se pinta sobre el campo del nombre.
 */
public class AddEditAddressActivity extends AuthenticatedActivity implements OnMapReadyCallback {

    public static final String EXTRA_ADDRESS_ID = "extra_address_id";
    public static final String EXTRA_ADDRESS_LABEL = "extra_address_label";
    public static final String EXTRA_ADDRESS_TEXT = "extra_address_text";
    public static final String EXTRA_ADDRESS_LAT = "extra_address_lat";
    public static final String EXTRA_ADDRESS_LNG = "extra_address_lng";

    private static final LatLng DEFAULT_POSITION = new LatLng(19.4326, -99.1332);
    private static final long SEARCH_DEBOUNCE_MS = 300;
    private static final int SHEET_CORNER_RADIUS_DP = 24;
    /** Fracción del recorrido a partir de la cual el modal empieza a teñir la status bar. */
    private static final float SHEET_FULLSCREEN_FADE_START = 0.85f;
    private static final int FLOATING_BUTTON_MARGIN_DP = 16;
    /** Recorrido que siempre le queda al modal por encima de su corte, para poder arrastrarlo. */
    private static final int SHEET_MIN_EXPAND_TRAVEL_DP = 56;

    private final PlacesAutocompleteService placesAutocompleteService = new PlacesAutocompleteService(this);
    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());

    private AddressRepository addressRepository;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;

    /** No nulo solo cuando se llegó desde la lista para editar una dirección existente. */
    private String editingAddressId;
    /** Al editar Casa/Trabajo el nombre no se toca: solo se corrige a qué punto apunta. */
    private boolean labelLocked;

    private BottomSheetBehavior<View> sheetBehavior;
    private View sheetContent;
    private View statusBarScrim;
    private GradientDrawable sheetBackground;
    private final float[] sheetCornerRadii = new float[8];
    private float sheetCornerRadiusPx;
    private int sheetTopInsetPx;
    private int sheetAvailableHeightPx;
    private int restingSheetHeightPx;
    private int lastPeekPx = -1;

    private View pin;
    private EditText inputSearch;
    private ImageView btnClearSearch;
    private LinearLayout containerPredictions;
    private View groupForm;
    private ChipGroup chipGroupLabel;
    private TextView textLabelSection;
    private TextInputLayout layoutLabelName;
    private TextInputEditText inputLabelName;
    private TextView textAddress;
    private MaterialButton btnSave;
    private MaterialButton btnDelete;

    private boolean searchModeActive;
    /**
     * Si el usuario movió el pin en esta sesión. Comparar coordenadas no sirve: la cámara que
     * devuelve el mapa no es bit a bit la que se le pidió, así que una dirección que nadie tocó
     * parecería haberse movido unos centímetros.
     */
    private boolean pinMoved;
    private OnBackPressedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_address);

        addressRepository = new RestAddressRepository(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        editingAddressId = getIntent().getStringExtra(EXTRA_ADDRESS_ID);
        boolean isEditing = editingAddressId != null;
        String initialLabel = getIntent().getStringExtra(EXTRA_ADDRESS_LABEL);
        AddressLabel initialPreset = AddressLabel.fromText(this, initialLabel);
        labelLocked = isEditing && initialPreset != AddressLabel.OTRO;

        bindViews();
        ((TextView) findViewById(R.id.text_address_title))
                .setText(isEditing ? R.string.address_edit_title_edit : R.string.address_edit_title_new);

        chipForLabel(initialPreset).setChecked(true);
        chipGroupLabel.setOnCheckedStateChangeListener((group, checkedIds) -> updateNameFieldVisibility());
        if (isEditing) {
            // Arranca con la dirección guardada, que es la del pin al abrir; desde ahí la va
            // reescribiendo el mapa.
            textAddress.setText(getIntent().getStringExtra(EXTRA_ADDRESS_TEXT));
            if (initialPreset == AddressLabel.OTRO) {
                inputLabelName.setText(initialLabel);
            }
        }
        applyLabelLock();
        updateNameFieldVisibility();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_my_location).setOnClickListener(v -> {
            pinMoved = true;
            centerOnLastKnownLocation();
        });
        btnSave.setOnClickListener(v -> saveAddress());
        findViewById(R.id.btn_cancel_address).setOnClickListener(v -> finish());
        btnDelete.setVisibility(isEditing ? View.VISIBLE : View.GONE);
        if (isEditing) {
            btnDelete.setOnClickListener(v -> confirmDelete());
        }

        // El orden importa: los callbacks del modal y del buscador se apoyan en backCallback.
        setUpBackHandling();
        setUpSearch();
        setUpBottomSheet();
        // También al editar una dirección "Otro": desde ahí se puede intentar pasarla a Casa o
        // Trabajo, y conviene saber de antemano si esos nombres están libres. Al editar la propia
        // Casa/Trabajo no hace falta — ahí los chips ni se ven.
        if (!labelLocked) {
            loadTakenLabels();
        }

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void bindViews() {
        pin = findViewById(R.id.img_address_pin);
        sheetContent = findViewById(R.id.sheet_content);
        statusBarScrim = findViewById(R.id.scrim_status_bar);
        inputSearch = findViewById(R.id.input_search_address);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        containerPredictions = findViewById(R.id.container_predictions);
        groupForm = findViewById(R.id.group_address_form);
        chipGroupLabel = findViewById(R.id.chip_group_label);
        textLabelSection = findViewById(R.id.text_label_section);
        layoutLabelName = findViewById(R.id.layout_label_name);
        inputLabelName = findViewById(R.id.input_label_name);
        textAddress = findViewById(R.id.text_address);
        btnSave = findViewById(R.id.btn_save_address);
        btnDelete = findViewById(R.id.btn_delete_address);
    }

    // ---------------------------------------------------------------------------------------
    // Etiqueta y nombre
    // ---------------------------------------------------------------------------------------

    /**
     * "Casa" y "Trabajo" son nombres reservados: solo puede haber uno de cada. En un alta nueva se
     * consulta qué hay guardado y se apagan los chips ya ocupados, en vez de dejar crear un
     * segundo "Casa" y descubrirlo después en la lista.
     */
    private void loadTakenLabels() {
        addressRepository.getAll(new ApiCallback<List<SavedAddress>>() {
            @Override
            public void onSuccess(List<SavedAddress> addresses) {
                for (SavedAddress address : addresses) {
                    AddressLabel preset = AddressLabel.fromText(AddEditAddressActivity.this, address.getLabel());
                    if (preset != AddressLabel.OTRO) {
                        disableChip(chipForLabel(preset));
                    }
                }
            }

            @Override
            public void onError(ApiException error) {
                // Sin la lista no se puede saber cuáles están ocupadas; se dejan los tres chips
                // habilitados en vez de bloquear el alta por un fallo de red.
            }
        });
    }

    private void disableChip(Chip chip) {
        chip.setEnabled(false);
        if (chip.isChecked()) {
            chipForLabel(AddressLabel.OTRO).setChecked(true);
        }
    }

    /** Al editar Casa/Trabajo desaparece la elección de nombre: solo queda mover la dirección. */
    private void applyLabelLock() {
        if (!labelLocked) {
            return;
        }
        chipGroupLabel.setVisibility(View.GONE);
        layoutLabelName.setVisibility(View.GONE);
        textLabelSection.setText(R.string.address_edit_label_locked);
    }

    private void updateNameFieldVisibility() {
        if (labelLocked) {
            return;
        }
        boolean isOther = chipGroupLabel.getCheckedChipId() == R.id.chip_otro;
        layoutLabelName.setVisibility(isOther ? View.VISIBLE : View.GONE);
    }

    private Chip chipForLabel(AddressLabel label) {
        switch (label) {
            case CASA:
                return findViewById(R.id.chip_casa);
            case TRABAJO:
                return findViewById(R.id.chip_trabajo);
            default:
                return findViewById(R.id.chip_otro);
        }
    }

    /** El texto que se guarda: el nombre del preset, o el que escribió el usuario en "Otro". */
    @Nullable
    private String resolveLabel() {
        if (labelLocked) {
            return getIntent().getStringExtra(EXTRA_ADDRESS_LABEL);
        }
        int checkedId = chipGroupLabel.getCheckedChipId();
        if (checkedId == R.id.chip_casa) {
            return getString(R.string.address_label_casa);
        }
        if (checkedId == R.id.chip_trabajo) {
            return getString(R.string.address_label_trabajo);
        }
        String custom = inputLabelName.getText() == null ? "" : inputLabelName.getText().toString().trim();
        return custom.isEmpty() ? null : custom;
    }

    // ---------------------------------------------------------------------------------------
    // Mapa
    // ---------------------------------------------------------------------------------------

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(this, googleMap);
        // El botón nativo del SDK se sustituye por btn_my_location, que sigue el tema y comparte
        // fila y estilo con el de volver.
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        if (hasLocationPermission()) {
            googleMap.setMyLocationEnabled(true);
        }
        googleMap.setPadding(0, sheetTopInsetPx, 0, Math.max(lastPeekPx, 0));

        if (editingAddressId != null) {
            double lat = getIntent().getDoubleExtra(EXTRA_ADDRESS_LAT, DEFAULT_POSITION.latitude);
            double lng = getIntent().getDoubleExtra(EXTRA_ADDRESS_LNG, DEFAULT_POSITION.longitude);
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(new LatLng(lat, lng), 16f)));
        } else {
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 15f)));
            centerOnLastKnownLocation();
        }

        googleMap.setOnCameraIdleListener(this::onCameraIdle);
        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                pinMoved = true;
            }
        });
    }

    /**
     * La dirección sigue al pin: al soltar el mapa se traduce el punto a texto. Es también lo que
     * rellena el campo tras elegir una sugerencia — así lo guardado siempre describe el punto que
     * de verdad se está guardando, no el texto de una búsqueda anterior.
     */
    private void onCameraIdle() {
        if (googleMap == null) {
            return;
        }
        GeocoderHelper.reverseGeocodeAsync(this, googleMap.getCameraPosition().target, address -> {
            if (address != null) {
                textAddress.setText(address);
            }
            // Si el geocodificador no devuelve nada se deja la última dirección buena: borrarla
            // dejaría la pantalla sin decir qué se va a guardar.
        });
    }

    @SuppressLint("MissingPermission")
    private void centerOnLastKnownLocation() {
        if (!hasLocationPermission()) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
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

    // ---------------------------------------------------------------------------------------
    // Buscador dentro del modal
    // ---------------------------------------------------------------------------------------

    private void setUpSearch() {
        // El input vive dentro de un NestedScrollView que además es el objetivo de un
        // BottomSheetBehavior: los dos interceptan el ACTION_DOWN para decidir si es scroll o
        // arrastre, y el primer toque se perdía (el clásico "hay que tocar dos veces").
        inputSearch.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        inputSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                enterSearchMode();
            }
        });
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                scheduleSearch(s.toString());
            }
        });
        btnClearSearch.setOnClickListener(v -> inputSearch.setText(""));
        findViewById(R.id.icon_search_leading).setOnClickListener(v -> inputSearch.requestFocus());

        // Escribir el nombre también necesita el modal arriba: si no, el teclado tapa justo el
        // campo que se está llenando. No entra en modo búsqueda — no hay sugerencias que dar.
        inputLabelName.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        inputLabelName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                sheetBehavior.setDraggable(true);
                sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                backCallback.setEnabled(true);
            }
        });
    }

    private void enterSearchMode() {
        if (searchModeActive) {
            return;
        }
        searchModeActive = true;
        backCallback.setEnabled(true);
        // Arrastrable solo mientras hay teclado: en reposo el formulario cabe entero en el corte,
        // y dejar subir el modal ahí solo enseñaría un hueco vacío debajo.
        sheetBehavior.setDraggable(true);
        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        showPredictions(Collections.emptyList());
    }

    /** Salida explícita (atrás, elegir una sugerencia): limpia, cierra el teclado y colapsa. */
    private void cancelSearch() {
        exitSearchModeUi();
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    /**
     * Limpia el estado de búsqueda sin tocar el behavior — sirve igual cuando el usuario arrastra
     * el modal hacia abajo, caso en el que ya está colapsado y volver a pedirlo sería redundante.
     */
    private void exitSearchModeUi() {
        searchDebounceHandler.removeCallbacksAndMessages(null);
        hideKeyboard();
        // El foco vuelve al contenedor (focusableInTouchMode) y no salta al siguiente campo, que
        // reabriría el teclado que se acaba de cerrar.
        sheetContent.requestFocus();
        inputLabelName.clearFocus();
        backCallback.setEnabled(false);
        if (!searchModeActive) {
            return;
        }
        inputSearch.setText("");
        inputSearch.clearFocus();
        searchModeActive = false;
        containerPredictions.setVisibility(View.GONE);
        containerPredictions.removeAllViews();
        groupForm.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(sheetContent.getWindowToken(), 0);
        }
    }

    private void scheduleSearch(String query) {
        searchDebounceHandler.removeCallbacksAndMessages(null);
        if (query.trim().isEmpty()) {
            showPredictions(Collections.emptyList());
            return;
        }
        LatLng bias = googleMap != null ? googleMap.getCameraPosition().target : null;
        searchDebounceHandler.postDelayed(() -> placesAutocompleteService.findPredictions(
                this, query, bias, this::showPredictions), SEARCH_DEBOUNCE_MS);
    }

    private void showPredictions(List<AutocompletePrediction> predictions) {
        boolean hasQuery = inputSearch.getText().length() > 0;
        containerPredictions.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
        // El formulario se aparta mientras hay sugerencias: son dos cosas que ocupan el mismo
        // sitio y solo una es la que el usuario está mirando.
        groupForm.setVisibility(hasQuery ? View.GONE : View.VISIBLE);
        containerPredictions.removeAllViews();
        if (!hasQuery) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AutocompletePrediction prediction : predictions) {
            View row = inflater.inflate(R.layout.item_place_prediction, containerPredictions, false);
            ((TextView) row.findViewById(R.id.text_prediction_primary)).setText(prediction.getPrimaryText(null));
            ((TextView) row.findViewById(R.id.text_prediction_secondary)).setText(prediction.getSecondaryText(null));
            row.setOnClickListener(v -> placesAutocompleteService.resolvePlace(this, prediction.getPlaceId(),
                    new PlacesAutocompleteService.ResultListener() {
                        @Override
                        public void onPlaceSelected(String address, double lat, double lng) {
                            onPlacePicked(address, lat, lng);
                        }
                    }));
            containerPredictions.addView(row);
        }
    }

    /** Elegida una sugerencia, la pantalla vuelve a su reposo con el pin ya puesto en el sitio. */
    private void onPlacePicked(String address, double lat, double lng) {
        pinMoved = true;
        textAddress.setText(address);
        cancelSearch();
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 16f));
        }
    }

    private void setUpBackHandling() {
        // Solo se activa con el teclado en pantalla: atrás cierra la búsqueda antes que la
        // pantalla, que es lo que espera quien acaba de abrir el buscador sin querer.
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                cancelSearch();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    // ---------------------------------------------------------------------------------------
    // Modal y medidas (mismo esquema que HomeFragment)
    // ---------------------------------------------------------------------------------------

    private void setUpBottomSheet() {
        View sheet = findViewById(R.id.sheet_container);
        sheetBehavior = BottomSheetBehavior.from(sheet);
        sheetBehavior.setHideable(false);
        sheetBehavior.setSkipCollapsed(false);
        sheetBehavior.setDraggable(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        sheetCornerRadiusPx = SHEET_CORNER_RADIUS_DP * getResources().getDisplayMetrics().density;
        // mutate() para no compartir el estado del drawable con las demás pantallas que usan
        // bg_sheet_top_rounded: aquí se le cambia el radio en caliente.
        if (sheet.getBackground() instanceof GradientDrawable) {
            sheetBackground = (GradientDrawable) sheet.getBackground().mutate();
            sheet.setBackground(sheetBackground);
        }

        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    applyFullScreenTransition(0f);
                    // Cubre que el usuario baje el modal con el dedo en vez de usar atrás.
                    exitSearchModeUi();
                    sheetBehavior.setDraggable(false);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    applyFullScreenTransition(1f);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                applyFullScreenTransition(slideOffset);
            }
        });

        ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::updateSheetStops;
        sheetContent.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    /** Tiñe la status bar y quita el redondeo conforme el modal llega arriba. 0 = abajo, 1 = tope. */
    private void applyFullScreenTransition(float slideOffset) {
        float progress = (slideOffset - SHEET_FULLSCREEN_FADE_START) / (1f - SHEET_FULLSCREEN_FADE_START);
        progress = Math.max(0f, Math.min(1f, progress));

        statusBarScrim.setAlpha(progress);
        if (sheetBackground == null) {
            return;
        }
        float radius = sheetCornerRadiusPx * (1f - progress);
        for (int i = 0; i < 4; i++) {
            sheetCornerRadii[i] = radius;
        }
        sheetBackground.setCornerRadii(sheetCornerRadii);
    }

    /**
     * El corte del modal es el alto de su contenido en reposo. Mientras se busca no se remide: con
     * las sugerencias en pantalla el contenido mide bastante más, y ese valor inflado se quedaría
     * como corte al volver del buscador, dejando el modal más arriba de su sitio.
     */
    private void updateSheetStops() {
        applyTopInsets();
        if (!searchModeActive) {
            int measured = sheetContent.getHeight();
            if (measured > 0) {
                restingSheetHeightPx = measured;
            }
        }
        int peekPx = clampPeek(restingSheetHeightPx);
        if (peekPx <= 0) {
            return;
        }
        if (peekPx != lastPeekPx) {
            lastPeekPx = peekPx;
            sheetBehavior.setPeekHeight(peekPx, true);
        }
        if (googleMap != null) {
            googleMap.setPadding(0, sheetTopInsetPx, 0, peekPx);
        }
        centerPinInViewport(peekPx);
    }

    /**
     * Impide que el corte alcance el alto del propio modal: si coinciden, colapsado y expandido
     * caen en el mismo punto, el arrastre se queda sin recorrido y el gesto se lo lleva el scroll
     * interno — el modal parece dejar de responder.
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

    /**
     * Insets explícitos, como en HomeFragment: el modal topa justo debajo de la status bar en vez
     * de meterse detrás de ella, y los flotantes bajan para no quedar tapados. Se acota la altura
     * del sheet en lugar de darle margen superior — con margen, el tope que calcula el behavior no
     * coincide con el del primer layout y el modal da un salto en el primer arrastre.
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

        View sheet = findViewById(R.id.sheet_container);
        int availableHeightPx = root.getHeight() - topInsetPx;
        ViewGroup.LayoutParams sheetParams = sheet.getLayoutParams();
        if (root.getHeight() > 0) {
            sheetAvailableHeightPx = availableHeightPx;
            if (sheetParams.height != availableHeightPx) {
                sheetParams.height = availableHeightPx;
                sheet.setLayoutParams(sheetParams);
            }
        }

        int marginPx = Math.round(FLOATING_BUTTON_MARGIN_DP * getResources().getDisplayMetrics().density);
        setTopMargin(findViewById(R.id.btn_back), topInsetPx + marginPx);
        setTopMargin(findViewById(R.id.btn_my_location), topInsetPx + marginPx);

        ViewGroup.LayoutParams scrimParams = statusBarScrim.getLayoutParams();
        if (scrimParams.height != topInsetPx) {
            scrimParams.height = topInsetPx;
            statusBarScrim.setLayoutParams(scrimParams);
        }
    }

    /**
     * El pin marca el punto que se va a guardar, y ése es el centro del <em>hueco visible</em> del
     * mapa (lo que decide su padding), no el centro de la pantalla. Sin esto el pin y el punto
     * real se separan justo lo que mide el modal.
     */
    private void centerPinInViewport(int sheetHeightPx) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) pin.getLayoutParams();
        if (params.topMargin != sheetTopInsetPx || params.bottomMargin != sheetHeightPx) {
            params.topMargin = sheetTopInsetPx;
            params.bottomMargin = sheetHeightPx;
            pin.setLayoutParams(params);
        }
    }

    private void setTopMargin(View view, int topMarginPx) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.topMargin != topMarginPx) {
            params.topMargin = topMarginPx;
            view.setLayoutParams(params);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Guardar y borrar
    // ---------------------------------------------------------------------------------------

    private void saveAddress() {
        String addressText = textAddress.getText().toString().trim();
        if (!ValidationHelper.isNotEmpty(addressText)) {
            Toast.makeText(this, R.string.address_edit_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }
        String label = resolveLabel();
        if (label == null) {
            layoutLabelName.setError(getString(R.string.address_edit_name_required_error));
            inputLabelName.requestFocus();
            return;
        }
        layoutLabelName.setError(null);
        if (googleMap == null) {
            return;
        }
        LatLng target = googleMap.getCameraPosition().target;

        LoadingButtonHelper.setLoading(btnSave, true);
        ApiCallback<SavedAddress> guardado = new ApiCallback<SavedAddress>() {
            @Override
            public void onSuccess(SavedAddress result) {
                finish();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnSave, false);
                onSaveFailed(error);
            }
        };

        if (editingAddressId != null) {
            saveEdit(label, addressText, target, guardado);
        } else {
            addressRepository.create(label, addressText, target.latitude, target.longitude, guardado);
        }
    }

    /**
     * Una sola escritura —antes esto era crear la nueva y borrar la vieja, y un fallo entre los dos
     * pasos dejaba duplicados o se llevaba la dirección por delante— y con <b>solo los campos que
     * cambiaron</b>.
     *
     * <p>Lo segundo importa por el índice único de nombres: si "solo moví el pin" viajara con el
     * nombre incluido, cada edición pasaría por la comprobación de unicidad sin ninguna necesidad.
     * No falla —Postgres no considera que una fila choque consigo misma al actualizarla, y hay una
     * prueba que lo fija— pero es una dependencia gratuita de ese detalle. Mandando solo lo que
     * cambió, mover el pin de "Casa" ni menciona el nombre.
     */
    private void saveEdit(String label, String addressText, LatLng target,
                           ApiCallback<SavedAddress> callback) {
        String labelDelta = label.equals(getIntent().getStringExtra(EXTRA_ADDRESS_LABEL)) ? null : label;
        String addressDelta =
                addressText.equals(getIntent().getStringExtra(EXTRA_ADDRESS_TEXT)) ? null : addressText;
        Double latDelta = pinMoved ? target.latitude : null;
        Double lngDelta = pinMoved ? target.longitude : null;

        if (labelDelta == null && addressDelta == null && latDelta == null) {
            // Guardar sin haber tocado nada es salir. El servidor rechaza el cuerpo vacío
            // (VALIDATION_ERROR) y tiene razón, así que ni se le pregunta.
            finish();
            return;
        }
        addressRepository.update(editingAddressId, labelDelta, addressDelta, latDelta, lngDelta, callback);
    }

    /**
     * El nombre repetido no es un fallo genérico: se señala sobre el campo que hay que cambiar, y
     * se abre si estaba escondido (puede llegar tras haber elegido Casa o Trabajo con la lista
     * desactualizada). El resto de errores sí son un aviso suelto — no hay nada que corregir ahí.
     */
    private void onSaveFailed(ApiException error) {
        if (error.getCode() == ApiErrorCode.FAVORITE_LABEL_TAKEN && !labelLocked) {
            chipForLabel(AddressLabel.OTRO).setChecked(true);
            updateNameFieldVisibility();
            layoutLabelName.setError(getString(R.string.address_edit_name_taken_error));
            inputLabelName.requestFocus();
            return;
        }
        Toast.makeText(this, R.string.address_edit_save_error, Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.address_list_delete_title)
                .setMessage(getString(R.string.address_list_delete_message_format,
                        getIntent().getStringExtra(EXTRA_ADDRESS_LABEL)))
                .setPositiveButton(R.string.address_list_delete_positive, (dialog, which) -> deleteAddress())
                .setNegativeButton(R.string.address_list_delete_negative, null)
                .show();
    }

    private void deleteAddress() {
        LoadingButtonHelper.setLoading(btnDelete, true);
        addressRepository.delete(editingAddressId, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                finish();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnDelete, false);
                Toast.makeText(AddEditAddressActivity.this, R.string.address_edit_delete_error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchDebounceHandler.removeCallbacksAndMessages(null);
    }
}
