package com.bng.drivo.ui.destination;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import com.bng.drivo.ui.auth.AuthenticatedActivity;
import androidx.core.content.ContextCompat;

import com.bng.drivo.R;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.util.GeocoderHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Deja al pasajero elegir el destino arrastrando el mapa bajo un pin fijo en el centro
 * (patrón estándar de apps de transporte). La dirección se resuelve con el Geocoder
 * nativo de Android (best-effort, sin costo ni dependencia nueva) — ver
 * docs/drivo-analisis-inicial.md para la migración futura a Places API.
 */
public class PickLocationOnMapActivity extends AuthenticatedActivity implements OnMapReadyCallback {

    public static final String EXTRA_ADDRESS = "extra_address";
    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LNG = "extra_lng";

    private static final LatLng DEFAULT_POSITION = new LatLng(19.4326, -99.1332);

    private GoogleMap googleMap;
    private TextView textSelectedAddress;
    private String lastResolvedAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_location);

        textSelectedAddress = findViewById(R.id.text_selected_address);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btn_confirm_location).setOnClickListener(v -> confirmSelectedLocation());

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

        LatLng start = DEFAULT_POSITION;
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(start, 15f)));
        centerOnLastKnownLocationIfAvailable();

        googleMap.setOnCameraMoveStartedListener(reason -> {
            lastResolvedAddress = null;
            textSelectedAddress.setText(R.string.pick_location_resolving_address);
        });
        googleMap.setOnCameraIdleListener(this::resolveAddressPreview);
        resolveAddressPreview();
    }

    private void resolveAddressPreview() {
        if (googleMap == null) {
            return;
        }
        LatLng target = googleMap.getCameraPosition().target;
        GeocoderHelper.reverseGeocodeAsync(this, target, address -> {
            lastResolvedAddress = address != null ? address : getString(R.string.pick_location_fallback_address);
            textSelectedAddress.setText(lastResolvedAddress);
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

    private void confirmSelectedLocation() {
        if (googleMap == null) {
            return;
        }
        LatLng target = googleMap.getCameraPosition().target;
        if (lastResolvedAddress != null) {
            returnResult(target, lastResolvedAddress);
        } else {
            GeocoderHelper.reverseGeocodeAsync(this, target, address -> returnResult(target, address));
        }
    }

    private void returnResult(LatLng target, String address) {
        Intent result = new Intent();
        result.putExtra(EXTRA_ADDRESS, address != null ? address : getString(R.string.pick_location_fallback_address));
        result.putExtra(EXTRA_LAT, target.latitude);
        result.putExtra(EXTRA_LNG, target.longitude);
        setResult(RESULT_OK, result);
        finish();
    }
}
