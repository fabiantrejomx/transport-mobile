package com.bng.drivo.ui.price;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.model.Quote;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.Waypoint;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.service.PlacesAutocompleteService;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.ui.map.RouteCamera;
import com.bng.drivo.ui.search.SearchingDriverActivity;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * "Confirma tu viaje": mapa de fondo + tarjetas flotantes (origen/parada/destino arriba,
 * tarifa/pago/solicitar abajo). La tarifa sugerida y la banda de negociación (floor/ceiling)
 * vienen siempre de POST /quotes — el cliente nunca calcula distancia ni precio, y la línea
 * entre puntos es solo una guía punteada recta, no una ruta real (ver openapi.yaml).
 */
public class ConfirmPriceActivity extends AuthenticatedActivity implements OnMapReadyCallback {

    public static final String EXTRA_ORIGIN = "extra_origin";
    public static final String EXTRA_DESTINATION = "extra_destination";
    public static final String EXTRA_PRICE = "extra_price";
    public static final String EXTRA_ORIGIN_LAT = "extra_origin_lat";
    public static final String EXTRA_ORIGIN_LNG = "extra_origin_lng";
    public static final String EXTRA_DESTINATION_LAT = "extra_destination_lat";
    public static final String EXTRA_DESTINATION_LNG = "extra_destination_lng";

    /** Aire alrededor de la ruta al encuadrarla, para que los pines no queden pegados al borde. */
    private static final int ROUTE_BOUNDS_PADDING_PX = 220;

    private TripRepository tripRepository;
    private final PlacesAutocompleteService placesAutocompleteService = new PlacesAutocompleteService(this);

    private String origin;
    private String destination;
    private double originLat;
    private double originLng;
    private double destinationLat;
    private double destinationLng;
    private Waypoint stop;

    private GoogleMap googleMap;
    private Quote currentQuote;
    private boolean requestingRide;
    private final RouteCamera routeCamera = new RouteCamera(ROUTE_BOUNDS_PADDING_PX);

    private TextView textAddStop;
    private View btnRemoveStop;
    private TextView textPriceAmount;
    private TextView textPriceMin;
    private TextView textPriceMax;
    private Slider slider;
    private View progressQuote;
    private MaterialButton btnRequestTrip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_price);

        tripRepository = new RestTripRepository(this);

        origin = getIntent().getStringExtra(EXTRA_ORIGIN);
        destination = getIntent().getStringExtra(EXTRA_DESTINATION);
        originLat = getIntent().getDoubleExtra(EXTRA_ORIGIN_LAT, 0);
        originLng = getIntent().getDoubleExtra(EXTRA_ORIGIN_LNG, 0);
        destinationLat = getIntent().getDoubleExtra(EXTRA_DESTINATION_LAT, 0);
        destinationLng = getIntent().getDoubleExtra(EXTRA_DESTINATION_LNG, 0);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.text_origin)).setText(origin);
        ((TextView) findViewById(R.id.text_destination)).setText(destination);

        textAddStop = findViewById(R.id.text_add_stop);
        btnRemoveStop = findViewById(R.id.btn_remove_stop);
        textPriceAmount = findViewById(R.id.text_price_amount);
        textPriceMin = findViewById(R.id.text_price_min);
        textPriceMax = findViewById(R.id.text_price_max);
        slider = findViewById(R.id.slider_price);
        progressQuote = findViewById(R.id.progress_quote);
        btnRequestTrip = findViewById(R.id.btn_request_trip);

        slider.addOnChangeListener((s, value, fromUser) -> textPriceAmount.setText(formatPrice(value)));

        findViewById(R.id.row_payment).setOnClickListener(v ->
                Toast.makeText(this, R.string.confirm_price_payment_coming_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.row_add_stop).setOnClickListener(v -> onStopRowClicked());
        btnRemoveStop.setOnClickListener(v -> removeStop());

        btnRequestTrip.setOnClickListener(v -> requestRide());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        loadQuote();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(this, googleMap);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        drawRouteGuide();
    }

    private void onStopRowClicked() {
        if (stop != null) {
            return;
        }
        placesAutocompleteService.launch(this, new PlacesAutocompleteService.ResultListener() {
            @Override
            public void onPlaceSelected(String address, double lat, double lng) {
                stop = new Waypoint(lat, lng, address);
                bindStopRow();
                drawRouteGuide();
                loadQuote();
            }
        });
    }

    private void removeStop() {
        stop = null;
        bindStopRow();
        drawRouteGuide();
        loadQuote();
    }

    private void bindStopRow() {
        if (stop != null) {
            textAddStop.setText(stop.getText());
            textAddStop.setTextColor(getColor(R.color.drivo_secondary));
            btnRemoveStop.setVisibility(View.VISIBLE);
        } else {
            textAddStop.setText(R.string.confirm_price_add_stop);
            textAddStop.setTextColor(getColor(R.color.drivo_success));
            btnRemoveStop.setVisibility(View.GONE);
        }
    }

    private void drawRouteGuide() {
        if (googleMap == null) {
            return;
        }
        googleMap.clear();

        List<LatLng> points = new ArrayList<>();
        points.add(new LatLng(originLat, originLng));
        if (stop != null) {
            points.add(new LatLng(stop.getLat(), stop.getLng()));
        }
        points.add(new LatLng(destinationLat, destinationLng));

        googleMap.addMarker(new MarkerOptions().position(points.get(0))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        if (stop != null) {
            googleMap.addMarker(new MarkerOptions().position(new LatLng(stop.getLat(), stop.getLng()))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        }
        googleMap.addMarker(new MarkerOptions().position(points.get(points.size() - 1))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        List<PatternItem> dashed = Arrays.asList(new Dash(20f), new Gap(12f));
        Polyline polyline = googleMap.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(8f)
                .color(getColor(R.color.drivo_success))
                .pattern(dashed));

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (LatLng point : points) {
            bounds.include(point);
        }
        Fragment mapFragment = getSupportFragmentManager().findFragmentById(R.id.map);
        routeCamera.frame(googleMap, bounds.build(), mapFragment != null ? mapFragment.getView() : null);
    }

    private List<Waypoint> currentWaypoints() {
        return stop != null ? Collections.singletonList(stop) : null;
    }

    private void loadQuote() {
        setFormEnabled(false);
        progressQuote.setVisibility(View.VISIBLE);

        tripRepository.createQuote(originLat, originLng, destinationLat, destinationLng, origin, destination,
                currentWaypoints(), new ApiCallback<Quote>() {
                    @Override
                    public void onSuccess(Quote quote) {
                        progressQuote.setVisibility(View.GONE);
                        bindQuote(quote);
                    }

                    @Override
                    public void onError(ApiException error) {
                        progressQuote.setVisibility(View.GONE);
                        Toast.makeText(ConfirmPriceActivity.this, R.string.confirm_price_quote_error,
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void bindQuote(Quote quote) {
        currentQuote = quote;

        float stepSize = 2f;
        float min = Math.round(quote.getFloor());
        float max = Math.round(quote.getCeiling());
        // El Slider de Material exige que (max - min) sea múltiplo exacto de stepSize.
        max -= (max - min) % stepSize;
        if (max <= min) {
            max = min + stepSize;
        }

        int floorPercent = quote.getSuggestedFare() > 0
                ? Math.round((float) (quote.getFloor() / quote.getSuggestedFare()) * 100) : 80;
        int ceilingPercent = quote.getSuggestedFare() > 0
                ? Math.round((float) (quote.getCeiling() / quote.getSuggestedFare()) * 100) : 140;

        textPriceMin.setText(formatMinMax(min, floorPercent));
        textPriceMax.setText(formatMinMax(max, ceilingPercent));

        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(stepSize);
        float snappedValue = snapToStep((float) quote.getSuggestedFare(), min, max, stepSize);
        slider.setValue(snappedValue);
        textPriceAmount.setText(formatPrice(snappedValue));

        setFormEnabled(true);
    }

    /**
     * El Slider de Material exige que todo valor asignado caiga exacto en la grilla
     * {@code min, min+stepSize, min+2*stepSize, ...} o lanza IllegalArgumentException — la
     * tarifa sugerida del servidor viene en pesos con centavos y casi nunca cae ahí sola.
     */
    private float snapToStep(float value, float min, float max, float stepSize) {
        float clamped = Math.max(min, Math.min(max, value));
        long steps = Math.round((clamped - min) / stepSize);
        return min + steps * stepSize;
    }

    private void requestRide() {
        if (currentQuote == null || requestingRide) {
            return;
        }
        requestingRide = true;
        setFormEnabled(false);
        LoadingButtonHelper.setLoading(btnRequestTrip, true);

        tripRepository.createRide(currentQuote.getId(), slider.getValue(), new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                goToSearching(ride);
            }

            @Override
            public void onError(ApiException error) {
                if (error.getCode() == ApiErrorCode.QUOTE_EXPIRED) {
                    recotizeAndRetry();
                    return;
                }
                requestingRide = false;
                setFormEnabled(true);
                LoadingButtonHelper.setLoading(btnRequestTrip, false);
                Toast.makeText(ConfirmPriceActivity.this, R.string.confirm_price_request_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    /** QUOTE_EXPIRED se resuelve recotizando una vez y reintentando, nunca como error crudo al usuario. */
    private void recotizeAndRetry() {
        float previousOffer = slider.getValue();
        tripRepository.createQuote(originLat, originLng, destinationLat, destinationLng, origin, destination,
                currentWaypoints(), new ApiCallback<Quote>() {
                    @Override
                    public void onSuccess(Quote quote) {
                        bindQuote(quote);
                        // bindQuote() reactiva el botón como si fuera la carga inicial de la
                        // pantalla — todavía estamos a mitad del reintento automático.
                        LoadingButtonHelper.setLoading(btnRequestTrip, true);
                        // Conservar la oferta que el pasajero ya había elegido, no la nueva
                        // tarifa sugerida — bindQuote() la pisó al reconstruir el slider.
                        float retryOffer = snapToStep(previousOffer, slider.getValueFrom(),
                                slider.getValueTo(), slider.getStepSize());
                        slider.setValue(retryOffer);
                        textPriceAmount.setText(formatPrice(retryOffer));

                        tripRepository.createRide(quote.getId(), retryOffer, new ApiCallback<Ride>() {
                            @Override
                            public void onSuccess(Ride ride) {
                                goToSearching(ride);
                            }

                            @Override
                            public void onError(ApiException error) {
                                requestingRide = false;
                                setFormEnabled(true);
                                LoadingButtonHelper.setLoading(btnRequestTrip, false);
                                Toast.makeText(ConfirmPriceActivity.this, R.string.confirm_price_request_error,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(ApiException error) {
                        requestingRide = false;
                        setFormEnabled(true);
                        LoadingButtonHelper.setLoading(btnRequestTrip, false);
                        Toast.makeText(ConfirmPriceActivity.this, R.string.confirm_price_request_error,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToSearching(Ride ride) {
        Intent intent = new Intent(this, SearchingDriverActivity.class);
        intent.putExtra(SearchingDriverActivity.EXTRA_RIDE_ID, ride.getId());
        intent.putExtra(EXTRA_ORIGIN, origin);
        intent.putExtra(EXTRA_DESTINATION, destination);
        intent.putExtra(EXTRA_PRICE, (float) slider.getValue());
        intent.putExtra(EXTRA_ORIGIN_LAT, originLat);
        intent.putExtra(EXTRA_ORIGIN_LNG, originLng);
        intent.putExtra(EXTRA_DESTINATION_LAT, destinationLat);
        intent.putExtra(EXTRA_DESTINATION_LNG, destinationLng);
        startActivity(intent);
        finish();
    }

    private void setFormEnabled(boolean enabled) {
        slider.setEnabled(enabled);
        btnRequestTrip.setEnabled(enabled);
    }

    private String formatPrice(float value) {
        return String.format(Locale.getDefault(), "$%.2f", value);
    }

    private String formatMinMax(float value, int percent) {
        return String.format(Locale.getDefault(), "$%.0f (%d%%)", value, percent);
    }
}
