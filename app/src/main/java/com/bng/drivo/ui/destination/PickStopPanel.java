package com.bng.drivo.ui.destination;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bng.drivo.R;
import com.bng.drivo.data.model.AddressLabel;
import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.service.PlacesAutocompleteService;
import com.bng.drivo.ui.map.MapPresenter;
import com.bng.drivo.util.GeocoderHelper;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.AutocompletePrediction;

import java.util.Collections;
import java.util.List;

/**
 * Paso "elige una parada": el mismo patrón de {@link PickLocationPanel} (pin fijo, mapa
 * arrastrable debajo) más las otras dos formas de elegir un punto que ya existían en Home para
 * el destino — escribir una dirección (con predicciones) o tocar una dirección guardada — para
 * que parada y destino ofrezcan siempre las mismas tres opciones.
 *
 * <p>El input hace las dos veces: por defecto refleja la dirección bajo el pin (se actualiza
 * solo con cada arrastre, ver {@link #onCameraIdle}), pero en cuanto el usuario escribe algo deja
 * de ser una vista previa y pasa a ser el buscador — {@code programmaticChange} es lo que
 * distingue un cambio de texto nuestro de uno del usuario para el {@link TextWatcher}.
 */
public class PickStopPanel {

    public interface Callbacks {
        void onStopConfirmed(@NonNull String address, double lat, double lng);

        /** El input ganó foco: pide al host expandir el modal, igual que el buscador de Home. */
        void onSearchExpandRequested();

        /** Búsqueda cancelada (atrás) sin elegir nada: pide al host volver a colapsar el modal. */
        void onSearchCollapseRequested();

        /** "Cancelar" en reposo: se sale del paso sin parada, igual que el botón atrás. */
        void onStopCancelled();
    }

    private static final long SEARCH_DEBOUNCE_MS = 300;
    /** Igual que HomeFragment.PEEK_ADDRESS_ROWS: no tiene sentido mostrar más aquí, sin scroll. */
    private static final int MAX_SAVED_ADDRESS_ROWS = 3;

    private final View panel;
    private final MapPresenter mapPresenter;
    private final PlacesAutocompleteService placesAutocompleteService;
    private final Callbacks callbacks;

    private final EditText input;
    private final ImageView clearButton;
    private final LinearLayout predictionsContainer;
    private final View defaultListGroup;
    private final View noResultsLabel;
    private final TextView savedAddressesLabel;
    private final LinearLayout savedAddressesContainer;
    private final View confirmButton;
    private final View cancelStepButton;
    private final View cancelSearchButton;

    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());

    private boolean active;
    /** true entre que el input gana foco y se cancela la búsqueda o se elige algo. */
    private boolean searching;
    /** Suprime el TextWatcher mientras el propio panel escribe el texto (no el usuario). */
    private boolean programmaticChange;
    @Nullable
    private String lastResolvedMapAddress;
    private List<SavedAddress> savedAddresses = Collections.emptyList();
    @Nullable
    private LatLng predictionBias;

    public PickStopPanel(@NonNull View panel, @NonNull MapPresenter mapPresenter,
                          @NonNull PlacesAutocompleteService placesAutocompleteService,
                          @NonNull Callbacks callbacks) {
        this.panel = panel;
        this.mapPresenter = mapPresenter;
        this.placesAutocompleteService = placesAutocompleteService;
        this.callbacks = callbacks;

        input = panel.findViewById(R.id.input_stop_query);
        clearButton = panel.findViewById(R.id.btn_stop_clear);
        predictionsContainer = panel.findViewById(R.id.container_stop_predictions);
        defaultListGroup = panel.findViewById(R.id.group_stop_default_list);
        noResultsLabel = panel.findViewById(R.id.text_stop_no_results);
        savedAddressesLabel = panel.findViewById(R.id.text_stop_saved_addresses_label);
        savedAddressesContainer = panel.findViewById(R.id.container_stop_saved_addresses);
        confirmButton = panel.findViewById(R.id.btn_confirm_stop);
        cancelStepButton = panel.findViewById(R.id.btn_cancel_stop);
        cancelSearchButton = panel.findViewById(R.id.btn_cancel_stop_search);

        confirmButton.setOnClickListener(v -> confirmMapPin());
        cancelStepButton.setOnClickListener(v -> callbacks.onStopCancelled());
        cancelSearchButton.setOnClickListener(v -> exitSearch());
        // Borrar la dirección solo tiene sentido para escribir otra, así que de paso deja el
        // campo enfocado y el teclado arriba en vez de exigir un segundo toque.
        clearButton.setOnClickListener(v -> {
            input.setText("");
            input.requestFocus();
            showKeyboard();
        });

        // Mismo caso que input_destination en Home: este EditText vive dentro de un
        // NestedScrollView que además es el target de un BottomSheetBehavior arrastrable, y ambos
        // interceptan el ACTION_DOWN para decidir si es scroll/drag antes de dejarlo llegar al
        // hijo — sin esto haría falta tocar dos veces para enfocar el campo.
        input.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                enterSearch();
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
                // La "x" se actualiza ANTES de la guarda: también cuando el texto lo pone el
                // panel (la dirección bajo el pin). Estando dentro de la guarda, el campo se
                // rellenaba con la dirección y la "x" no aparecía, así que para escribir otra
                // había que borrarla letra a letra.
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (programmaticChange) {
                    return;
                }
                scheduleSearch(s.toString());
            }
        });
    }

    /**
     * Entrada al paso: no mueve la cámara (la ruta origen-destino se deja tal cual, ver el
     * comentario de PICK_STOP en HomeFragment.applyStep). {@code savedAddresses} ya viene
     * cargado de Home, para no repetir la llamada a red; {@code originBias} sesga (no restringe)
     * las predicciones hacia el viaje en curso.
     */
    public void show(@NonNull List<SavedAddress> savedAddresses, @Nullable LatLng originBias) {
        active = true;
        searching = false;
        this.savedAddresses = savedAddresses;
        this.predictionBias = originBias;
        // Descarta cualquier texto de una visita anterior al panel (la vista es la misma, solo
        // se oculta entre pasos) hasta que resolveAddressPreview() traiga la dirección real.
        lastResolvedMapAddress = null;
        setInputText(panel.getContext().getString(R.string.pick_location_resolving_address));
        applySearchChrome(false);
        bindSavedAddresses();
        showDefaultList();
        resolveAddressPreview();
    }

    /**
     * true mientras hay una búsqueda en curso. El host lo consulta para no tomar el alto del
     * modal en ese momento: buscando, el panel mide otra cosa — la lista de predicciones
     * sustituye a la de direcciones guardadas y puede ser bastante más alta — y ese alto no es
     * el tamaño al que el modal debe volver (ver HomeFragment.updateSheetStops).
     */
    public boolean isSearching() {
        return searching;
    }

    public void hide() {
        active = false;
        searching = false;
        searchDebounceHandler.removeCallbacksAndMessages(null);
        applySearchChrome(false);
        input.clearFocus();
        hideKeyboard();
    }

    /**
     * Despachado por el host cuando el paso PICK_STOP recibe "atrás". Si había una búsqueda en
     * curso la cancela y consume el back (igual que Home con su propio buscador); si no, no hace
     * nada y le devuelve el control al host para que sea éste quien salga del paso.
     */
    public boolean handleBackPressed() {
        if (!searching) {
            return false;
        }
        exitSearch();
        return true;
    }

    private void enterSearch() {
        if (searching) {
            return;
        }
        searching = true;
        // El campo llega prellenado con la dirección del pin: sin seleccionarla, lo que el
        // usuario escriba se intercala dentro de ella y la búsqueda sale con un texto mezclado.
        // Seleccionada, la primera tecla la reemplaza entera, que es lo que se espera al empezar
        // a escribir una dirección nueva; tocar el campo otra vez sigue permitiendo editarla.
        input.selectAll();
        applySearchChrome(true);
        callbacks.onSearchExpandRequested();
    }

    /** Solo el estado de "hay una búsqueda en curso": foco, teclado, debounce y aviso al host. */
    private void leaveSearchMode() {
        searchDebounceHandler.removeCallbacksAndMessages(null);
        if (!searching) {
            return;
        }
        searching = false;
        applySearchChrome(false);
        input.clearFocus();
        hideKeyboard();
        callbacks.onSearchCollapseRequested();
    }

    /**
     * Los botones comparten ranura y nunca se solapan: en reposo mandan "Confirmar parada" y
     * "Cancelar" (salir del paso sin parada), y buscando los sustituye "Cancelar búsqueda", que
     * solo deshace la búsqueda. Etiquetas distintas a propósito — un mismo "Cancelar" que a
     * veces cierra el paso y a veces solo la búsqueda no diría cuál de las dos hace.
     */
    private void applySearchChrome(boolean searching) {
        confirmButton.setVisibility(searching ? View.GONE : View.VISIBLE);
        cancelStepButton.setVisibility(searching ? View.GONE : View.VISIBLE);
        cancelSearchButton.setVisibility(searching ? View.VISIBLE : View.GONE);
    }

    /** Cancelar sin elegir nada: el input vuelve a reflejar el pin, no queda lo que se escribió. */
    private void exitSearch() {
        leaveSearchMode();
        showDefaultList();
        setInputText(lastResolvedMapAddress != null ? lastResolvedMapAddress
                : panel.getContext().getString(R.string.pick_location_resolving_address));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                panel.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                panel.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /** Llamado por el host desde su único {@code OnCameraMoveStartedListener} del mapa. */
    public void onCameraMoveStarted() {
        if (!active) {
            return;
        }
        // Arrastrar el mapa es una decisión explícita de volver a elegir por el pin: se
        // descarta cualquier búsqueda a medio escribir y se vuelve a la vista por defecto. El
        // host ya filtra los reencuadres programáticos (ver HomeFragment.onMapReady) — de
        // llegar aquí es siempre un arrastre real del usuario.
        leaveSearchMode();
        showDefaultList();
        lastResolvedMapAddress = null;
        setInputText(panel.getContext().getString(R.string.pick_location_resolving_address));
    }

    /** Llamado por el host desde su único {@code OnCameraIdleListener} del mapa. */
    public void onCameraIdle() {
        if (active) {
            resolveAddressPreview();
        }
    }

    private void resolveAddressPreview() {
        GoogleMap map = mapPresenter.getMap();
        if (map == null) {
            return;
        }
        LatLng target = map.getCameraPosition().target;
        GeocoderHelper.reverseGeocodeAsync(panel.getContext(), target, address -> {
            if (!active) {
                return;
            }
            lastResolvedMapAddress = address != null ? address : panel.getContext().getString(
                    R.string.pick_location_fallback_address);
            setInputText(lastResolvedMapAddress);
        });
    }

    private void setInputText(String text) {
        programmaticChange = true;
        input.setText(text);
        // Cursor al inicio, no al final: son direcciones largas que no caben en el campo, y
        // dejarlo al final las muestra por su cola ("...Valle Dorado, Ver., México") en vez de
        // por el número y la calle, que es lo que identifica el punto.
        input.setSelection(0);
        programmaticChange = false;
    }

    private void confirmMapPin() {
        GoogleMap map = mapPresenter.getMap();
        if (map == null) {
            return;
        }
        LatLng target = map.getCameraPosition().target;
        if (lastResolvedMapAddress != null) {
            callbacks.onStopConfirmed(lastResolvedMapAddress, target.latitude, target.longitude);
        } else {
            GeocoderHelper.reverseGeocodeAsync(panel.getContext(), target, address -> {
                String resolved = address != null ? address : panel.getContext().getString(
                        R.string.pick_location_fallback_address);
                callbacks.onStopConfirmed(resolved, target.latitude, target.longitude);
            });
        }
    }

    // ---------------------------------------------------------------------------------------
    // Buscador (predicciones) y direcciones guardadas
    // ---------------------------------------------------------------------------------------

    private void scheduleSearch(String query) {
        searchDebounceHandler.removeCallbacksAndMessages(null);
        if (query.trim().isEmpty()) {
            showDefaultList();
            return;
        }
        searchDebounceHandler.postDelayed(() -> placesAutocompleteService.findPredictions(
                panel.getContext(), query, predictionBias, predictions -> {
                    if (active) {
                        bindPredictions(predictions);
                        showPredictionsList(!predictions.isEmpty());
                    }
                }), SEARCH_DEBOUNCE_MS);
    }

    private void showDefaultList() {
        predictionsContainer.setVisibility(View.GONE);
        noResultsLabel.setVisibility(View.GONE);
        defaultListGroup.setVisibility(View.VISIBLE);
    }

    /** Con la búsqueda ya resuelta manda la lista, o el aviso de "sin resultados" si vino vacía. */
    private void showPredictionsList(boolean hasResults) {
        defaultListGroup.setVisibility(View.GONE);
        predictionsContainer.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        noResultsLabel.setVisibility(hasResults ? View.GONE : View.VISIBLE);
    }

    private void bindPredictions(List<AutocompletePrediction> predictions) {
        predictionsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(panel.getContext());
        for (AutocompletePrediction prediction : predictions) {
            View row = inflater.inflate(R.layout.item_place_prediction, predictionsContainer, false);
            ((TextView) row.findViewById(R.id.text_prediction_primary)).setText(prediction.getPrimaryText(null));
            ((TextView) row.findViewById(R.id.text_prediction_secondary)).setText(prediction.getSecondaryText(null));
            row.setOnClickListener(v -> placesAutocompleteService.resolvePlace(
                    panel.getContext(), prediction.getPlaceId(), new PlacesAutocompleteService.ResultListener() {
                        @Override
                        public void onPlaceSelected(String address, double lat, double lng) {
                            if (active) {
                                callbacks.onStopConfirmed(address, lat, lng);
                            }
                        }
                    }));
            predictionsContainer.addView(row);
        }
    }

    private void bindSavedAddresses() {
        savedAddressesContainer.removeAllViews();
        boolean hasSaved = !savedAddresses.isEmpty();
        savedAddressesLabel.setVisibility(hasSaved ? View.VISIBLE : View.GONE);
        savedAddressesContainer.setVisibility(hasSaved ? View.VISIBLE : View.GONE);
        if (!hasSaved) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(panel.getContext());
        int count = Math.min(savedAddresses.size(), MAX_SAVED_ADDRESS_ROWS);
        for (int i = 0; i < count; i++) {
            SavedAddress address = savedAddresses.get(i);
            View row = inflater.inflate(R.layout.item_saved_address, savedAddressesContainer, false);

            AddressLabel icon = AddressLabel.fromText(panel.getContext(), address.getLabel());
            ((TextView) row.findViewById(R.id.text_address_emoji)).setText(icon.getEmoji());
            ((TextView) row.findViewById(R.id.text_address_label)).setText(address.getLabel());
            ((TextView) row.findViewById(R.id.text_address_line)).setText(address.getAddress());

            row.setOnClickListener(v -> callbacks.onStopConfirmed(
                    address.getAddress(), address.getLat(), address.getLng()));
            savedAddressesContainer.addView(row);
        }
    }
}
