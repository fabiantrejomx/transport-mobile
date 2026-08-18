package com.bng.drivo.ui.destination;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import androidx.core.content.ContextCompat;

import com.bng.drivo.R;
import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.repository.AddressRepository;
import com.bng.drivo.data.repository.MockAddressRepository;
import com.bng.drivo.ui.price.ConfirmPriceActivity;
import com.bng.drivo.util.ColorUtils;
import com.bng.drivo.service.PlacesAutocompleteService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

/**
 * Pantalla "Elige tu destino": réplica de la función pSetDestino() del prototipo, más la
 * opción de elegir el destino directamente desde el mapa. El campo de destino abre el
 * overlay de autocompletado del Places SDK for Android (Places API (New) habilitada en
 * Cloud Console) en vez de un input de texto libre — ver docs/drivo-analisis-inicial.md.
 */
public class SetDestinationActivity extends AuthenticatedActivity {

    // Ciudad de México — respaldo cuando no hay permiso/última ubicación conocida del origen.
    private static final LatLng DEFAULT_ORIGIN = new LatLng(19.4326, -99.1332);

    private EditText inputDestination;
    private AddressRepository addressRepository;
    private FusedLocationProviderClient fusedLocationClient;

    private LatLng originLocation = DEFAULT_ORIGIN;
    private double destinationLat;
    private double destinationLng;

    private final ActivityResultLauncher<Intent> pickLocationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onLocationPicked);
    private final PlacesAutocompleteService placesAutocompleteService = new PlacesAutocompleteService(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_destino);

        addressRepository = new MockAddressRepository(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        loadLastKnownOrigin();

        inputDestination = findViewById(R.id.input_destination);
        inputDestination.setFocusable(false);
        inputDestination.setOnClickListener(v -> placesAutocompleteService.launch(this, this::onPlaceSelected));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.row_pick_on_map).setOnClickListener(v ->
                pickLocationLauncher.launch(new Intent(this, PickLocationOnMapActivity.class)));

        loadSavedAddresses();

        findViewById(R.id.btn_confirm_destination).setOnClickListener(v -> {
            String destination = inputDestination.getText().toString().trim();
            if (destination.isEmpty()) {
                Toast.makeText(this, R.string.set_destino_empty_error, Toast.LENGTH_SHORT).show();
                return;
            }
            goToConfirmPrice(destination);
        });

        findViewById(R.id.row_add_stop).setOnClickListener(v ->
                Toast.makeText(this, R.string.set_destino_add_stop_coming_soon, Toast.LENGTH_SHORT).show());
    }

    private void loadSavedAddresses() {
        LinearLayout container = findViewById(R.id.container_saved_addresses);
        container.removeAllViews();

        List<SavedAddress> addresses = addressRepository.getAll();
        findViewById(R.id.text_saved_addresses_label).setVisibility(
                addresses.isEmpty() ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < addresses.size(); i++) {
            SavedAddress address = addresses.get(i);
            View row = inflater.inflate(R.layout.item_saved_address, container, false);

            ((TextView) row.findViewById(R.id.text_address_emoji)).setText(address.getLabel().getEmoji());
            ((TextView) row.findViewById(R.id.text_address_label)).setText(address.getLabel().getDisplayNameRes());
            ((TextView) row.findViewById(R.id.text_address_line)).setText(address.getAddress());

            row.setOnClickListener(v -> {
                destinationLat = address.getLat();
                destinationLng = address.getLng();
                goToConfirmPrice(address.getAddress());
            });

            container.addView(row);

            if (i < addresses.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(
                        ColorUtils.resolveThemeColor(this, com.google.android.material.R.attr.colorOutline));
                container.addView(divider);
            }
        }
    }

    private void onPlaceSelected(String address, double lat, double lng) {
        destinationLat = lat;
        destinationLng = lng;
        inputDestination.setText(address);
    }

    private void onLocationPicked(androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        String address = result.getData().getStringExtra(PickLocationOnMapActivity.EXTRA_ADDRESS);
        destinationLat = result.getData().getDoubleExtra(PickLocationOnMapActivity.EXTRA_LAT, destinationLat);
        destinationLng = result.getData().getDoubleExtra(PickLocationOnMapActivity.EXTRA_LNG, destinationLng);
        if (address != null) {
            inputDestination.setText(address);
            inputDestination.setSelection(address.length());
        }
    }

    @SuppressLint("MissingPermission")
    private void loadLastKnownOrigin() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                originLocation = new LatLng(location.getLatitude(), location.getLongitude());
            }
        });
    }

    private void goToConfirmPrice(String destination) {
        Intent intent = new Intent(this, ConfirmPriceActivity.class);
        intent.putExtra(ConfirmPriceActivity.EXTRA_ORIGIN, getString(R.string.home_origin_placeholder));
        intent.putExtra(ConfirmPriceActivity.EXTRA_DESTINATION, destination);
        intent.putExtra(ConfirmPriceActivity.EXTRA_ORIGIN_LAT, originLocation.latitude);
        intent.putExtra(ConfirmPriceActivity.EXTRA_ORIGIN_LNG, originLocation.longitude);
        intent.putExtra(ConfirmPriceActivity.EXTRA_DESTINATION_LAT, destinationLat);
        intent.putExtra(ConfirmPriceActivity.EXTRA_DESTINATION_LNG, destinationLng);
        startActivity(intent);
        finish();
    }
}
