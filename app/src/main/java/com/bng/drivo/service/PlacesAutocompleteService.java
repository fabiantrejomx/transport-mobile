package com.bng.drivo.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Envuelve el overlay de autocompletado prediseñado del Places SDK for Android
 * (Autocomplete.IntentBuilder) para no repetir el registro del ActivityResultLauncher
 * y el parseo del resultado en cada pantalla que necesita buscar una dirección
 * (ConfirmPriceActivity, AddEditAddressActivity).
 *
 * Nota: Autocomplete/AutocompleteActivity está marcado @Deprecated en el SDK 5.x en favor
 * de PlaceAutocomplete (API Kotlin-first que sólo devuelve un placeId/AutocompletePrediction
 * y requiere una llamada adicional a PlacesClient.fetchPlace). Se mantiene la API clásica
 * a propósito: sigue siendo funcional, resuelve dirección + lat/lng en un solo paso sin
 * PlacesClient adicional, y evita la superficie más compleja de la "Places UI Kit" nueva
 * que el usuario pidió explícitamente no usar salvo necesidad.
 *
 * También expone la API programática (findAutocompletePredictions/fetchPlace) que usa
 * HomeFragment para mostrar predicciones dentro de su propio input en vez del overlay.
 */
@SuppressWarnings("deprecation")
public class PlacesAutocompleteService {

    private static final String TAG = "PlacesAutocomplete";
    private static final List<Place.Field> FIELDS =
            Arrays.asList(Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION);
    /** ~0.45° de lado (~50km) alrededor del origen — sesga sin restringir los resultados. */
    private static final double BIAS_DEGREES = 0.45;

    public interface ResultListener {
        void onPlaceSelected(String address, double lat, double lng);

        default void onCancelled() {
        }
    }

    public interface PredictionsListener {
        void onPredictions(List<AutocompletePrediction> predictions);
    }

    private final ActivityResultLauncher<Intent> launcher;
    private ResultListener resultListener;
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;

    /** {@code caller} es una Activity o un Fragment — ambos implementan ActivityResultCaller. */
    public PlacesAutocompleteService(ActivityResultCaller caller) {
        launcher = caller.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleResult(result));
    }

    public void launch(Context context, ResultListener listener) {
        this.resultListener = listener;
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, FIELDS)
                .build(context);
        launcher.launch(intent);
    }

    private void handleResult(ActivityResult result) {
        if (resultListener == null) {
            return;
        }
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Place place = Autocomplete.getPlaceFromIntent(result.getData());
            String address = place.getFormattedAddress();
            LatLng latLng = place.getLocation();
            if (address != null && latLng != null) {
                resultListener.onPlaceSelected(address, latLng.latitude, latLng.longitude);
                return;
            }
        } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR && result.getData() != null) {
            Log.e(TAG, "Error en Places Autocomplete: " + Autocomplete.getStatusFromIntent(result.getData()));
        }
        resultListener.onCancelled();
    }

    private void ensureClient(Context context) {
        if (placesClient == null) {
            placesClient = Places.createClient(context.getApplicationContext());
        }
    }

    /**
     * Predicciones de autocompletado para el input propio de HomeFragment (sin overlay).
     * {@code origin}, si se da, sesga (no restringe) los resultados hacia esa zona — útil
     * para priorizar direcciones cerca del pasajero.
     */
    public void findPredictions(Context context, String query, @Nullable LatLng origin, PredictionsListener listener) {
        ensureClient(context);
        if (sessionToken == null) {
            sessionToken = AutocompleteSessionToken.newInstance();
        }

        FindAutocompletePredictionsRequest.Builder requestBuilder = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(query);
        if (origin != null) {
            LatLngBounds bounds = new LatLngBounds(
                    new LatLng(origin.latitude - BIAS_DEGREES, origin.longitude - BIAS_DEGREES),
                    new LatLng(origin.latitude + BIAS_DEGREES, origin.longitude + BIAS_DEGREES));
            requestBuilder.setOrigin(origin)
                    .setLocationBias(RectangularBounds.newInstance(bounds));
        }

        placesClient.findAutocompletePredictions(requestBuilder.build())
                .addOnSuccessListener(response -> listener.onPredictions(response.getAutocompletePredictions()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error buscando predicciones: " + e.getMessage());
                    listener.onPredictions(Collections.emptyList());
                });
    }

    /** Resuelve un placeId de {@link #findPredictions} a dirección + lat/lng y cierra la sesión. */
    public void resolvePlace(Context context, String placeId, ResultListener listener) {
        ensureClient(context);
        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, FIELDS)
                .setSessionToken(sessionToken)
                .build();
        sessionToken = null;

        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    String address = place.getFormattedAddress();
                    LatLng latLng = place.getLocation();
                    if (address != null && latLng != null) {
                        listener.onPlaceSelected(address, latLng.latitude, latLng.longitude);
                    } else {
                        listener.onCancelled();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error resolviendo lugar: " + e.getMessage());
                    listener.onCancelled();
                });
    }
}
