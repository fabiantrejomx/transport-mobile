package com.bng.drivo.ui.trip;

import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bng.drivo.R;
import com.bng.drivo.util.ColorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "¡Llegaste a tu destino!": réplica de pFinalizado() del prototipo (recibo + calificación
 * de 5 estrellas + comentario opcional). Único punto de salida hacia Home, como pReset()
 * en el prototipo — ActiveTripActivity ya se cerró al abrir esta pantalla, así que Home
 * queda como única actividad debajo en el back stack.
 */
public class FinishedTripActivity extends AppCompatActivity {

    private static final int STAR_COUNT = 5;

    private final List<TextView> starViews = new ArrayList<>();
    private int rating = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finished_trip);

        String initials = getIntent().getStringExtra(ActiveTripActivity.EXTRA_DRIVER_INITIALS);
        String name = getIntent().getStringExtra(ActiveTripActivity.EXTRA_DRIVER_NAME);
        float price = getIntent().getFloatExtra(ActiveTripActivity.EXTRA_PRICE, 0f);

        ((TextView) findViewById(R.id.text_driver_avatar)).setText(initials);
        ((TextView) findViewById(R.id.text_with_driver))
                .setText(getString(R.string.finished_trip_with_driver, name));
        ((TextView) findViewById(R.id.text_total_amount))
                .setText(String.format(Locale.getDefault(), "$%.2f", price));

        setUpStars();

        findViewById(R.id.btn_submit).setOnClickListener(v -> finish());
    }

    private void setUpStars() {
        LinearLayout container = findViewById(R.id.container_stars);
        int sizePx = (int) (8 * getResources().getDisplayMetrics().density);

        for (int i = 1; i <= STAR_COUNT; i++) {
            TextView star = new TextView(this);
            star.setText("★");
            star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f);
            star.setPadding(sizePx, 0, sizePx, 0);
            int starIndex = i;
            star.setOnClickListener(v -> setRating(starIndex));
            container.addView(star);
            starViews.add(star);
        }
        updateStars();
    }

    private void setRating(int newRating) {
        rating = newRating;
        updateStars();
    }

    private void updateStars() {
        int selectedColor = ColorUtils.resolveThemeColor(this, com.google.android.material.R.attr.colorSecondary);
        int unselectedColor = ColorUtils.resolveThemeColor(this, com.google.android.material.R.attr.colorOutline);
        for (int i = 0; i < starViews.size(); i++) {
            starViews.get(i).setTextColor(i < rating ? selectedColor : unselectedColor);
        }
    }
}
