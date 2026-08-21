package com.bng.drivo.ui.search;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.model.Offer;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.FirestoreRideRealtimeRepository;
import com.bng.drivo.data.repository.RealtimeSubscription;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.RideRealtimeRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.ui.map.MapStyler;
import com.bng.drivo.ui.map.RouteCamera;
import com.bng.drivo.ui.price.ConfirmPriceActivity;
import com.bng.drivo.ui.trip.ActiveTripActivity;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;
import java.util.Locale;

/**
 * "Buscando conductores…": radar 1 a 1 (réplica de pRadar() del prototipo) sobre el canal en
 * vivo de rides/{id}/offers (ver openapi.yaml, "Canal en vivo"). Se muestra una sola tarjeta
 * a la vez — la de menor queue_position —, nunca una lista de ofertas simultáneas.
 */
public class SearchingDriverActivity extends AuthenticatedActivity implements OnMapReadyCallback {

    public static final String EXTRA_RIDE_ID = "extra_ride_id";

    private static final long RADAR_PULSE_DURATION_MS = 1600L;
    /** Aire alrededor de la ruta al encuadrarla, para que los pines no queden pegados al borde. */
    private static final int ROUTE_BOUNDS_PADDING_PX = 220;

    private final RideRealtimeRepository realtimeRepository = new FirestoreRideRealtimeRepository();
    private TripRepository tripRepository;
    private RealtimeSubscription offersSubscription;
    private ValueAnimator radarAnimator;
    private CountDownTimer expiryTimer;

    private GoogleMap googleMap;
    private final RouteCamera routeCamera = new RouteCamera(ROUTE_BOUNDS_PADDING_PX);
    private String rideId;
    private String origin;
    private String destination;
    private double originLat;
    private double originLng;
    private double destinationLat;
    private double destinationLng;

    private boolean actionInFlight;
    private MaterialButton btnAcceptDriver;
    private MaterialButton btnRejectDriver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_searching);

        tripRepository = new RestTripRepository(this);

        rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        origin = getIntent().getStringExtra(ConfirmPriceActivity.EXTRA_ORIGIN);
        destination = getIntent().getStringExtra(ConfirmPriceActivity.EXTRA_DESTINATION);
        originLat = getIntent().getDoubleExtra(ConfirmPriceActivity.EXTRA_ORIGIN_LAT, 0);
        originLng = getIntent().getDoubleExtra(ConfirmPriceActivity.EXTRA_ORIGIN_LNG, 0);
        destinationLat = getIntent().getDoubleExtra(ConfirmPriceActivity.EXTRA_DESTINATION_LAT, 0);
        destinationLng = getIntent().getDoubleExtra(ConfirmPriceActivity.EXTRA_DESTINATION_LNG, 0);

        if (rideId == null) {
            finish();
            return;
        }

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        startRadarPulse();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        MapStyler.apply(this, googleMap);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setAllGesturesEnabled(false);

        LatLng originPoint = new LatLng(originLat, originLng);
        LatLng destinationPoint = new LatLng(destinationLat, destinationLng);
        googleMap.addMarker(new MarkerOptions().position(originPoint)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        googleMap.addMarker(new MarkerOptions().position(destinationPoint)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        LatLngBounds bounds = new LatLngBounds.Builder().include(originPoint).include(destinationPoint).build();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        routeCamera.frame(googleMap, bounds, mapFragment != null ? mapFragment.getView() : null);
    }

    /** Animación puramente decorativa (dos anillos que laten) — no depende de ningún dato. */
    private void startRadarPulse() {
        View outer = findViewById(R.id.radar_ring_outer);
        View inner = findViewById(R.id.radar_ring_inner);

        radarAnimator = ValueAnimator.ofFloat(0f, 1f);
        radarAnimator.setDuration(RADAR_PULSE_DURATION_MS);
        radarAnimator.setRepeatCount(ValueAnimator.INFINITE);
        radarAnimator.setInterpolator(new LinearInterpolator());
        radarAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            float scale = 0.85f + fraction * 0.3f;
            outer.setScaleX(scale);
            outer.setScaleY(scale);
            outer.setAlpha(0.2f * (1f - fraction));
            float innerFraction = (fraction + 0.5f) % 1f;
            float innerScale = 0.85f + innerFraction * 0.3f;
            inner.setScaleX(innerScale);
            inner.setScaleY(innerScale);
            inner.setAlpha(0.3f * (1f - innerFraction));
        });
        radarAnimator.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (rideId != null) {
            offersSubscription = realtimeRepository.observeOffers(rideId, this::onOffersChanged);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (offersSubscription != null) {
            offersSubscription.stop();
            offersSubscription = null;
        }
        cancelExpiryTimer();
    }

    private void onOffersChanged(List<Offer> offers) {
        if (actionInFlight) {
            // Ya se disparó accept/reject sobre la tarjeta actual; no la reemplaces a medias.
            return;
        }

        Offer current = offers.isEmpty() ? null : offers.get(0);
        findViewById(R.id.layout_searching).setVisibility(current == null ? View.VISIBLE : View.GONE);
        findViewById(R.id.layout_results).setVisibility(current == null ? View.GONE : View.VISIBLE);

        android.widget.LinearLayout container = findViewById(R.id.container_drivers);
        container.removeAllViews();
        cancelExpiryTimer();
        if (current != null) {
            container.addView(buildDriverCard(current));
        }
    }

    private View buildDriverCard(Offer offer) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_driver_offer, null, false);

        String ratingText = offer.getDriverRating() != null
                ? String.format(Locale.getDefault(), " · ★%.1f", offer.getDriverRating()) : "";
        String vehicleText = joinNonNull(" ", offer.getVehicleBrand(), offer.getVehicleModel(), offer.getVehicleColor());
        String details = joinNonNull(" · ", vehicleText, offer.getVehiclePlate());

        ((TextView) card.findViewById(R.id.text_driver_counter))
                .setText(getString(R.string.searching_counter, offer.getQueuePosition(), offer.getQueueTotal()));
        ((TextView) card.findViewById(R.id.text_driver_avatar)).setText(initialsFor(offer.getDriverName()));
        ((TextView) card.findViewById(R.id.text_driver_name)).setText(offer.getDriverName() + ratingText);
        ((TextView) card.findViewById(R.id.text_driver_details)).setText(details);
        ((TextView) card.findViewById(R.id.text_driver_price))
                .setText(String.format(Locale.getDefault(), "$%.2f", offer.getAmount()));

        TextView textEta = card.findViewById(R.id.text_driver_eta);
        if (offer.getEtaMin() != null) {
            textEta.setText(getString(R.string.searching_eta_min, offer.getEtaMin()));
            textEta.setVisibility(View.VISIBLE);
        } else {
            textEta.setVisibility(View.GONE);
        }

        startExpiryCountdown(card.findViewById(R.id.progress_offer_expiry), offer.getExpiresAtMillis());

        btnAcceptDriver = card.findViewById(R.id.btn_accept_driver);
        btnRejectDriver = card.findViewById(R.id.btn_reject_driver);
        btnAcceptDriver.setOnClickListener(v -> acceptOffer(offer));
        btnRejectDriver.setOnClickListener(v -> rejectOffer(offer));

        return card;
    }

    /** Cosmético: el contrato es explícito en que la verdad es expires_at en el servidor, esta
     * barra solo comunica la urgencia — si nunca llega a 0 porque Firestore ya reemplazó la
     * tarjeta antes, no pasa nada. */
    private void startExpiryCountdown(ProgressBar progressBar, Long expiresAtMillis) {
        cancelExpiryTimer();
        if (expiresAtMillis == null) {
            progressBar.setVisibility(View.GONE);
            return;
        }
        long totalMs = expiresAtMillis - System.currentTimeMillis();
        if (totalMs <= 0) {
            progressBar.setProgress(0);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setMax(1000);
        progressBar.setProgress(1000);

        expiryTimer = new CountDownTimer(totalMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                progressBar.setProgress((int) (1000 * millisUntilFinished / totalMs));
            }

            @Override
            public void onFinish() {
                progressBar.setProgress(0);
            }
        };
        expiryTimer.start();
    }

    private void cancelExpiryTimer() {
        if (expiryTimer != null) {
            expiryTimer.cancel();
            expiryTimer = null;
        }
    }

    private void acceptOffer(Offer offer) {
        setActionInFlight(true);
        LoadingButtonHelper.setLoading(btnAcceptDriver, true);
        btnRejectDriver.setEnabled(false);
        tripRepository.acceptOffer(rideId, offer.getOfferId(), new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                goToActiveTrip(ride);
            }

            @Override
            public void onError(ApiException error) {
                setActionInFlight(false);
                ApiErrorCode code = error.getCode();
                if (code == ApiErrorCode.DRIVER_NO_LONGER_AVAILABLE || code == ApiErrorCode.RIDE_ALREADY_TAKEN
                        || code == ApiErrorCode.OFFER_EXPIRED) {
                    // Sin diálogo de error por contrato: el listener de Firestore ya va a
                    // reemplazar esta tarjeta cuando el servidor actualice/borre el documento.
                    showWaitingForNext();
                    return;
                }
                LoadingButtonHelper.setLoading(btnAcceptDriver, false);
                btnRejectDriver.setEnabled(true);
                Toast.makeText(SearchingDriverActivity.this, R.string.searching_accept_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void rejectOffer(Offer offer) {
        setActionInFlight(true);
        LoadingButtonHelper.setLoading(btnRejectDriver, true);
        btnAcceptDriver.setEnabled(false);
        tripRepository.rejectOffer(rideId, offer.getOfferId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setActionInFlight(false);
                showWaitingForNext();
            }

            @Override
            public void onError(ApiException error) {
                setActionInFlight(false);
                LoadingButtonHelper.setLoading(btnRejectDriver, false);
                btnAcceptDriver.setEnabled(true);
                Toast.makeText(SearchingDriverActivity.this, R.string.searching_reject_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void setActionInFlight(boolean inFlight) {
        actionInFlight = inFlight;
    }

    private void showWaitingForNext() {
        findViewById(R.id.layout_searching).setVisibility(View.VISIBLE);
        findViewById(R.id.layout_results).setVisibility(View.GONE);
        ((android.widget.LinearLayout) findViewById(R.id.container_drivers)).removeAllViews();
        cancelExpiryTimer();
    }

    private void goToActiveTrip(Ride ride) {
        Intent intent = new Intent(this, ActiveTripActivity.class);
        intent.putExtra(ActiveTripActivity.EXTRA_RIDE_ID, rideId);
        intent.putExtra(ActiveTripActivity.EXTRA_DRIVER_INITIALS, initialsFor(ride.getDriverName()));
        intent.putExtra(ActiveTripActivity.EXTRA_DRIVER_NAME, ride.getDriverName());
        intent.putExtra(ActiveTripActivity.EXTRA_DRIVER_DETAILS,
                joinNonNull(" ", ride.getVehicleBrand(), ride.getVehicleModel(), ride.getVehicleColor()));
        intent.putExtra(ActiveTripActivity.EXTRA_PRICE,
                ride.getAgreedFare() != null ? ride.getAgreedFare().floatValue() : 0f);
        intent.putExtra(ActiveTripActivity.EXTRA_ORIGIN, origin);
        intent.putExtra(ActiveTripActivity.EXTRA_DESTINATION, destination);
        intent.putExtra(ActiveTripActivity.EXTRA_ORIGIN_LAT, originLat);
        intent.putExtra(ActiveTripActivity.EXTRA_ORIGIN_LNG, originLng);
        intent.putExtra(ActiveTripActivity.EXTRA_DESTINATION_LAT, destinationLat);
        intent.putExtra(ActiveTripActivity.EXTRA_DESTINATION_LNG, destinationLng);
        startActivity(intent);
        finish();
    }

    private String initialsFor(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length && initials.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return initials.toString();
    }

    private String joinNonNull(String separator, String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                if (result.length() > 0) {
                    result.append(separator);
                }
                result.append(part);
            }
        }
        return result.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (radarAnimator != null) {
            radarAnimator.cancel();
        }
    }
}
