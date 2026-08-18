package com.bng.drivo.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.gms.maps.model.LatLng;

import java.util.Arrays;
import java.util.List;

/**
 * Envuelve el overlay de autocompletado prediseñado del Places SDK for Android
 * (Autocomplete.IntentBuilder) para no repetir el registro del ActivityResultLauncher
 * y el parseo del resultado en cada pantalla que necesita buscar una dirección
 * (SetDestinationActivity, AddEditAddressActivity).
 *
 * Nota: Autocomplete/AutocompleteActivity está marcado @Deprecated en el SDK 5.x en favor
 * de PlaceAutocomplete (API Kotlin-first que sólo devuelve un placeId/AutocompletePrediction
 * y requiere una llamada adicional a PlacesClient.fetchPlace). Se mantiene la API clásica
 * a propósito: sigue siendo funcional, resuelve dirección + lat/lng en un solo paso sin
 * PlacesClient adicional, y evita la superficie más compleja de la "Places UI Kit" nueva
 * que el usuario pidió explícitamente no usar salvo necesidad.
 */
@SuppressWarnings("deprecation")
public class PlacesAutocompleteService {

    private static final String TAG = "PlacesAutocomplete";
    private static final List<Place.Field> FIELDS =
            Arrays.asList(Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION);

    public interface ResultListener {
        void onPlaceSelected(String address, double lat, double lng);

        default void onCancelled() {
        }
    }

    private final ActivityResultLauncher<Intent> launcher;
    private ResultListener resultListener;

    public PlacesAutocompleteService(AppCompatActivity activity) {
        launcher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleResult(activity, result));
    }

    public void launch(Context context, ResultListener listener) {
        this.resultListener = listener;
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, FIELDS)
                .build(context);
        launcher.launch(intent);
    }

    private void handleResult(Context context, ActivityResult result) {
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
}
