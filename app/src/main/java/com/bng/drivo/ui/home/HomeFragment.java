package com.bng.drivo.ui.home;

import android.Manifest;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

    private FusedLocationProviderClient fusedLocationClient;
    private GoogleMap googleMap;
    private BottomSheetBehavior<View> sheetBehavior;
    private LatLng originLocation = DEFAULT_POSITION;

    private AddressRepository addressRepository;
    private final PlacesAutocompleteService placesAutocompleteService = new PlacesAutocompleteService(this);

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

        setUpBottomSheet(view);
        setUpDestinationSearch(view);
        loadGreeting(view);
        loadSavedAddresses(view);
        loadRecentTrips(view);

        view.findViewById(R.id.btn_open_drawer).setOnClickListener(v ->
                ((HomeActivity) requireActivity()).openDrawer());

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

    /**
     * El tope de colapso del modal se mide en tiempo real a partir de group_peek_content
     * (saludo + buscador + elegir-en-mapa + direcciones guardadas) en vez de un dp fijo
     * adivinado, para que siempre corte justo ahí sin importar cuántas direcciones haya.
     *
     * <p>El listener NO se auto-remueve: direcciones guardadas llega async (loadSavedAddresses),
     * así que el primer layout mide el contenido antes de que esas filas existan — si se
     * removiera tras la primera medición, el peek quedaba fijo en esa altura corta y el modal
     * nunca llegaba a mostrar las direcciones sin estirarlo a mano.
     */
    private void setUpBottomSheet(View root) {
        View sheet = root.findViewById(R.id.sheet_container);
        sheetBehavior = BottomSheetBehavior.from(sheet);
        sheetBehavior.setHideable(false);
        sheetBehavior.setSkipCollapsed(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        View peekContent = root.findViewById(R.id.group_peek_content);
        peekContent.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int peekHeightPx = peekContent.getHeight();
            if (peekHeightPx <= 0) {
                return;
            }
            sheetBehavior.setPeekHeight(peekHeightPx);
            if (googleMap != null) {
                googleMap.setPadding(0, 0, 0, peekHeightPx);
            }
        });
    }

    private void setUpDestinationSearch(View root) {
        root.findViewById(R.id.row_search_destination).setOnClickListener(v ->
                placesAutocompleteService.launch(requireContext(), new PlacesAutocompleteService.ResultListener() {
                    @Override
                    public void onPlaceSelected(String address, double lat, double lng) {
                        goToConfirmPrice(address, lat, lng);
                    }
                }));

        root.findViewById(R.id.row_pick_on_map).setOnClickListener(v ->
                pickLocationLauncher.launch(new Intent(requireContext(), PickLocationOnMapActivity.class)));
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
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);

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
