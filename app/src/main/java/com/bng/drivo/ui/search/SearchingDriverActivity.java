package com.bng.drivo.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bng.drivo.R;
import com.bng.drivo.ui.price.ConfirmPriceActivity;
import com.bng.drivo.ui.trip.ActiveTripActivity;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

/**
 * "Buscando conductores…": réplica extendida de pRadar() del prototipo. El prototipo
 * muestra un único conductor con oferta y aceptar/rechazar; aquí se listan varias ofertas
 * de conductores (datos de muestra) entre las que el pasajero elige, por indicación del
 * cliente. El matching real llegará con el backend/Firebase.
 */
public class SearchingDriverActivity extends AppCompatActivity {

    private static final long SEARCH_DELAY_MS = 1800L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private String origin;
    private String destination;
    private float basePrice;
    private double originLat;
    private double originLng;
    private double destinationLat;
    private double destinationLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_searching);

        origin = getIntent().getStringExtra(ConfirmPriceActivity.EXTRA_ORIGIN);
        destination = getIntent().getStringExtra(ConfirmPriceActivity.EXTRA_DESTINATION);
        basePrice = getIntent().getFloatExtra(ConfirmPriceActivity.EXTRA_PRICE, 0f);
        originLat = getIntent().getDoubleExtra(ConfirmPriceActivity.EXTRA_ORIGIN_LAT, 0);
        originLng = getIntent().getDoubleExtra(ConfirmPriceActivity.EXTRA_ORIGIN_LNG, 0);
        destinationLat = getIntent().getDoubleExtra(ConfirmPriceActivity.EXTRA_DESTINATION_LAT, 0);
        destinationLng = getIntent().getDoubleExtra(ConfirmPriceActivity.EXTRA_DESTINATION_LNG, 0);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        handler.postDelayed(this::showResults, SEARCH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void showResults() {
        findViewById(R.id.layout_searching).setVisibility(View.GONE);
        findViewById(R.id.layout_results).setVisibility(View.VISIBLE);

        android.widget.LinearLayout container = findViewById(R.id.container_drivers);
        LayoutInflater inflater = LayoutInflater.from(this);

        addDriverOffer(inflater, container, "JP", "Juan P. · ★4.9",
                "Nissan Versa Blanco · YKV-889 · 4 min", basePrice + 12);
        addDriverOffer(inflater, container, "MG", "María G. · ★4.8",
                "Chevrolet Aveo Gris · TRR-214 · 6 min", basePrice + 8);
        addDriverOffer(inflater, container, "CR", "Carlos R. · ★5.0",
                "Kia Rio Rojo · XPT-552 · 3 min", basePrice + 20);
    }

    private void addDriverOffer(LayoutInflater inflater, android.widget.LinearLayout container,
                                 String initials, String name, String details, float price) {
        View card = inflater.inflate(R.layout.item_driver_offer, container, false);

        ((TextView) card.findViewById(R.id.text_driver_avatar)).setText(initials);
        ((TextView) card.findViewById(R.id.text_driver_name)).setText(name);
        ((TextView) card.findViewById(R.id.text_driver_details)).setText(details);
        ((TextView) card.findViewById(R.id.text_driver_price))
                .setText(String.format(Locale.getDefault(), "$%.2f", price));

        card.findViewById(R.id.btn_select_driver).setOnClickListener(v -> {
            Intent intent = new Intent(this, ActiveTripActivity.class);
            intent.putExtra(ActiveTripActivity.EXTRA_DRIVER_INITIALS, initials);
            intent.putExtra(ActiveTripActivity.EXTRA_DRIVER_NAME, name);
            intent.putExtra(ActiveTripActivity.EXTRA_DRIVER_DETAILS, details);
            intent.putExtra(ActiveTripActivity.EXTRA_PRICE, price);
            intent.putExtra(ActiveTripActivity.EXTRA_ORIGIN, origin);
            intent.putExtra(ActiveTripActivity.EXTRA_DESTINATION, destination);
            intent.putExtra(ActiveTripActivity.EXTRA_ORIGIN_LAT, originLat);
            intent.putExtra(ActiveTripActivity.EXTRA_ORIGIN_LNG, originLng);
            intent.putExtra(ActiveTripActivity.EXTRA_DESTINATION_LAT, destinationLat);
            intent.putExtra(ActiveTripActivity.EXTRA_DESTINATION_LNG, destinationLng);
            startActivity(intent);
            finish();
        });

        container.addView(card);
    }
}
