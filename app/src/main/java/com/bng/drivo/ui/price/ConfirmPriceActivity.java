package com.bng.drivo.ui.price;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.model.Quote;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.ui.search.SearchingDriverActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.slider.Slider;

import java.util.Locale;

/**
 * "Confirma tu viaje": réplica de pConfirmarPrecio() del prototipo. La tarifa sugerida y la
 * banda de negociación (floor/ceiling) vienen siempre de POST /quotes — el cliente nunca
 * calcula distancia ni precio (ver docs/analisis-inicial.md y openapi.yaml).
 */
public class ConfirmPriceActivity extends AuthenticatedActivity {

    public static final String EXTRA_ORIGIN = "extra_origin";
    public static final String EXTRA_DESTINATION = "extra_destination";
    public static final String EXTRA_PRICE = "extra_price";
    public static final String EXTRA_ORIGIN_LAT = "extra_origin_lat";
    public static final String EXTRA_ORIGIN_LNG = "extra_origin_lng";
    public static final String EXTRA_DESTINATION_LAT = "extra_destination_lat";
    public static final String EXTRA_DESTINATION_LNG = "extra_destination_lng";

    private TripRepository tripRepository;

    private String origin;
    private String destination;
    private double originLat;
    private double originLng;
    private double destinationLat;
    private double destinationLng;

    private Quote currentQuote;
    private boolean requestingRide;

    private TextView textPriceAmount;
    private TextView textPriceMin;
    private TextView textPriceMax;
    private Slider slider;
    private View progressQuote;
    private View btnRequestTrip;

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

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.text_origin)).setText(origin);
        ((TextView) findViewById(R.id.text_destination)).setText(destination);

        textPriceAmount = findViewById(R.id.text_price_amount);
        textPriceMin = findViewById(R.id.text_price_min);
        textPriceMax = findViewById(R.id.text_price_max);
        slider = findViewById(R.id.slider_price);
        progressQuote = findViewById(R.id.progress_quote);
        btnRequestTrip = findViewById(R.id.btn_request_trip);

        slider.addOnChangeListener((s, value, fromUser) -> textPriceAmount.setText(formatPrice(value)));

        findViewById(R.id.row_payment).setOnClickListener(v ->
                Toast.makeText(this, R.string.confirm_price_payment_coming_soon, Toast.LENGTH_SHORT).show());

        btnRequestTrip.setOnClickListener(v -> requestRide());

        loadQuote();
    }

    private void loadQuote() {
        setFormEnabled(false);
        progressQuote.setVisibility(View.VISIBLE);

        tripRepository.createQuote(originLat, originLng, destinationLat, destinationLng, origin, destination,
                new ApiCallback<Quote>() {
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
        textPriceAmount.setText(formatPrice((float) quote.getSuggestedFare()));

        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(stepSize);
        slider.setValue(Math.max(min, Math.min(max, (float) quote.getSuggestedFare())));

        setFormEnabled(true);
    }

    private void requestRide() {
        if (currentQuote == null || requestingRide) {
            return;
        }
        requestingRide = true;
        setFormEnabled(false);
        progressQuote.setVisibility(View.VISIBLE);

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
                progressQuote.setVisibility(View.GONE);
                setFormEnabled(true);
                Toast.makeText(ConfirmPriceActivity.this, R.string.confirm_price_request_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    /** QUOTE_EXPIRED se resuelve recotizando una vez y reintentando, nunca como error crudo al usuario. */
    private void recotizeAndRetry() {
        tripRepository.createQuote(originLat, originLng, destinationLat, destinationLng, origin, destination,
                new ApiCallback<Quote>() {
                    @Override
                    public void onSuccess(Quote quote) {
                        bindQuote(quote);
                        double clampedOffer = Math.max(quote.getFloor(), Math.min(quote.getCeiling(), slider.getValue()));
                        slider.setValue((float) clampedOffer);

                        tripRepository.createRide(quote.getId(), clampedOffer, new ApiCallback<Ride>() {
                            @Override
                            public void onSuccess(Ride ride) {
                                goToSearching(ride);
                            }

                            @Override
                            public void onError(ApiException error) {
                                requestingRide = false;
                                progressQuote.setVisibility(View.GONE);
                                setFormEnabled(true);
                                Toast.makeText(ConfirmPriceActivity.this, R.string.confirm_price_request_error,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(ApiException error) {
                        requestingRide = false;
                        progressQuote.setVisibility(View.GONE);
                        setFormEnabled(true);
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
