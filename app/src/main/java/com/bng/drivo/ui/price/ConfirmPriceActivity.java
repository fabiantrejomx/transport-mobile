package com.bng.drivo.ui.price;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

import com.bng.drivo.R;
import com.bng.drivo.ui.search.SearchingDriverActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.slider.Slider;

import java.util.Locale;

/**
 * "Confirma tu viaje": réplica de pConfirmarPrecio() del prototipo. Tarifa sugerida
 * ajustable entre 80% y 140% de una tarifa base fija (el cálculo real con Routes API /
 * TRAFFIC_AWARE_OPTIMAL queda para cuando exista backend — ver docs/drivo-analisis-inicial.md).
 */
public class ConfirmPriceActivity extends AuthenticatedActivity {

    public static final String EXTRA_ORIGIN = "extra_origin";
    public static final String EXTRA_DESTINATION = "extra_destination";
    public static final String EXTRA_PRICE = "extra_price";
    public static final String EXTRA_ORIGIN_LAT = "extra_origin_lat";
    public static final String EXTRA_ORIGIN_LNG = "extra_origin_lng";
    public static final String EXTRA_DESTINATION_LAT = "extra_destination_lat";
    public static final String EXTRA_DESTINATION_LNG = "extra_destination_lng";

    private static final float BASE_PRICE = 278f;
    private static final float MIN_FACTOR = 0.8f;
    private static final float MAX_FACTOR = 1.4f;

    private String origin;
    private String destination;
    private double originLat;
    private double originLng;
    private double destinationLat;
    private double destinationLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_price);

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

        float stepSize = 2f;
        float min = Math.round(BASE_PRICE * MIN_FACTOR);
        float max = Math.round(BASE_PRICE * MAX_FACTOR);
        // El Slider de Material exige que (max - min) sea múltiplo exacto de stepSize,
        // o lanza IllegalStateException al medir la vista; se recorta el máximo si no calza.
        max -= (max - min) % stepSize;

        TextView priceAmount = findViewById(R.id.text_price_amount);
        ((TextView) findViewById(R.id.text_price_min)).setText(formatMinMax(min, 80));
        ((TextView) findViewById(R.id.text_price_max)).setText(formatMinMax(max, 140));
        priceAmount.setText(formatPrice(BASE_PRICE));

        Slider slider = findViewById(R.id.slider_price);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(stepSize);
        slider.setValue(BASE_PRICE);
        slider.addOnChangeListener((s, value, fromUser) -> priceAmount.setText(formatPrice(value)));

        findViewById(R.id.row_payment).setOnClickListener(v ->
                Toast.makeText(this, R.string.confirm_price_payment_coming_soon, Toast.LENGTH_SHORT).show());

        findViewById(R.id.btn_request_trip).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchingDriverActivity.class);
            intent.putExtra(EXTRA_ORIGIN, origin);
            intent.putExtra(EXTRA_DESTINATION, destination);
            intent.putExtra(EXTRA_PRICE, slider.getValue());
            intent.putExtra(EXTRA_ORIGIN_LAT, originLat);
            intent.putExtra(EXTRA_ORIGIN_LNG, originLng);
            intent.putExtra(EXTRA_DESTINATION_LAT, destinationLat);
            intent.putExtra(EXTRA_DESTINATION_LNG, destinationLng);
            startActivity(intent);
            finish();
        });
    }

    private String formatPrice(float value) {
        return String.format(Locale.getDefault(), "$%.2f", value);
    }

    private String formatMinMax(float value, int percent) {
        return String.format(Locale.getDefault(), "$%.0f (%d%%)", value, percent);
    }
}
