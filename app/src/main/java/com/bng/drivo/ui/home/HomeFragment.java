package com.bng.drivo.ui.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bng.drivo.R;
import com.bng.drivo.ui.destination.SetDestinationActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.bng.drivo.ui.map.MapStyler;
import com.google.android.material.snackbar.Snackbar;

import java.util.Map;

/**
 * Pestaña Home del pasajero: mapa en vivo + campo de destino.
 * Réplica de la función pHome() del prototipo navegable, movida a Fragment para que
 * conviva con la barra de navegación inferior permanente (ver HomeActivity).
 */
public class HomeFragment extends Fragment implements OnMapReadyCallback {

    // Ciudad de México como punto de partida por defecto, antes de obtener la ubicación real.
    private static final LatLng DEFAULT_POSITION = new LatLng(19.4326, -99.1332);

    private FusedLocationProviderClient fusedLocationClient;
    private GoogleMap googleMap;

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

        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setUpDestinationCard(view);

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

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(requireContext(), googleMap);
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 15f)));

        if (hasLocationPermission()) {
            showMyLocation();
        }
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
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LatLng here = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(here, 16f));
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

    private void setUpDestinationCard(View root) {
        View.OnClickListener goToSetDestination = v ->
                startActivity(new Intent(requireContext(), SetDestinationActivity.class));

        root.findViewById(R.id.field_origin).setOnClickListener(goToSetDestination);
        root.findViewById(R.id.field_destination).setOnClickListener(goToSetDestination);
        root.findViewById(R.id.btn_request_trip).setOnClickListener(goToSetDestination);
    }
}
