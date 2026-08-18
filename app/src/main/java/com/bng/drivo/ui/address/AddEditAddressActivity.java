package com.bng.drivo.ui.address;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bng.drivo.R;
import com.bng.drivo.data.model.AddressLabel;
import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.repository.AddressRepository;
import com.bng.drivo.data.repository.MockAddressRepository;
import com.bng.drivo.service.PlacesAutocompleteService;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.util.GeocoderHelper;
import com.bng.drivo.util.ValidationHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AddEditAddressActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_ADDRESS_ID = "extra_address_id";

    private static final LatLng DEFAULT_POSITION = new LatLng(19.4326, -99.1332);

    private AddressRepository addressRepository;
    private ChipGroup chipGroupLabel;
    private EditText inputAddress;
    private GoogleMap googleMap;
    private SavedAddress editingAddress;

    private final PlacesAutocompleteService placesAutocompleteService = new PlacesAutocompleteService(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_address);

        addressRepository = new MockAddressRepository(this);

        String addressId = getIntent().getStringExtra(EXTRA_ADDRESS_ID);
        if (addressId != null) {
            editingAddress = findAddressById(addressId);
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(editingAddress != null
                ? R.string.address_edit_title_edit
                : R.string.address_edit_title_new);

        chipGroupLabel = findViewById(R.id.chip_group_label);
        inputAddress = findViewById(R.id.input_address);

        Chip initialChip = chipForLabel(editingAddress != null ? editingAddress.getLabel() : AddressLabel.OTRO);
        initialChip.setChecked(true);

        if (editingAddress != null) {
            inputAddress.setText(editingAddress.getAddress());
        }

        findViewById(R.id.text_search_address).setOnClickListener(v ->
                placesAutocompleteService.launch(this, this::onPlaceSelected));

        findViewById(R.id.btn_save_address).setOnClickListener(v -> saveAddress());

        android.widget.Button btnDelete = findViewById(R.id.btn_delete_address);
        if (editingAddress != null) {
            btnDelete.setVisibility(android.view.View.VISIBLE);
            btnDelete.setOnClickListener(v -> confirmDelete());
        }

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(this, googleMap);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        if (editingAddress != null) {
            LatLng target = new LatLng(editingAddress.getLat(), editingAddress.getLng());
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(target, 16f)));
        } else {
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 15f)));
            centerOnLastKnownLocationIfAvailable();
        }

        googleMap.setOnCameraIdleListener(this::onCameraIdle);
    }

    private void onPlaceSelected(String address, double lat, double lng) {
        if (googleMap == null) {
            return;
        }
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 16f));
    }

    private void onCameraIdle() {
        if (googleMap == null) {
            return;
        }
        LatLng target = googleMap.getCameraPosition().target;
        GeocoderHelper.reverseGeocodeAsync(this, target, address -> {
            if (address != null) {
                inputAddress.setText(address);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void centerOnLastKnownLocationIfAvailable() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            return;
        }
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && googleMap != null) {
                LatLng here = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(here, 16f));
            }
        });
    }

    private void saveAddress() {
        String addressText = inputAddress.getText().toString().trim();
        if (!ValidationHelper.isNotEmpty(addressText)) {
            Toast.makeText(this, R.string.address_edit_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (googleMap == null) {
            return;
        }
        LatLng target = googleMap.getCameraPosition().target;
        AddressLabel label = labelForCheckedChip();

        SavedAddress toSave = editingAddress != null
                ? editingAddress
                : new SavedAddress(label, addressText, target.latitude, target.longitude);
        toSave.setLabel(label);
        toSave.setAddress(addressText);
        toSave.setLat(target.latitude);
        toSave.setLng(target.longitude);

        addressRepository.save(toSave);
        finish();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.address_list_delete_title)
                .setMessage(R.string.address_list_delete_message)
                .setPositiveButton(R.string.address_list_delete_positive, (dialog, which) -> {
                    addressRepository.delete(editingAddress.getId());
                    finish();
                })
                .setNegativeButton(R.string.address_list_delete_negative, null)
                .show();
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

    private AddressLabel labelForCheckedChip() {
        int checkedId = chipGroupLabel.getCheckedChipId();
        if (checkedId == R.id.chip_casa) {
            return AddressLabel.CASA;
        } else if (checkedId == R.id.chip_trabajo) {
            return AddressLabel.TRABAJO;
        }
        return AddressLabel.OTRO;
    }

    private SavedAddress findAddressById(String id) {
        for (SavedAddress address : addressRepository.getAll()) {
            if (address.getId().equals(id)) {
                return address;
            }
        }
        return null;
    }
}
