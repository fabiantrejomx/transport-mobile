package com.bng.drivo.ui.trip;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.util.ColorUtils;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "¡Llegaste a tu destino!": réplica de pFinalizado() del prototipo (recibo + calificación
 * de 5 estrellas + comentario opcional). Único punto de salida hacia Home, como pReset()
 * en el prototipo — ActiveTripActivity ya se cerró al abrir esta pantalla, así que Home
 * queda como única actividad debajo en el back stack.
 */
public class FinishedTripActivity extends AuthenticatedActivity {

    private static final int STAR_COUNT = 5;

    private final List<TextView> starViews = new ArrayList<>();
    private int rating = 0;
    private String rideId;
    private TripRepository tripRepository;
    private MaterialButton btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finished_trip);

        tripRepository = new RestTripRepository(this);
        rideId = getIntent().getStringExtra(ActiveTripActivity.EXTRA_RIDE_ID);

        String initials = getIntent().getStringExtra(ActiveTripActivity.EXTRA_DRIVER_INITIALS);
        String name = getIntent().getStringExtra(ActiveTripActivity.EXTRA_DRIVER_NAME);
        float price = getIntent().getFloatExtra(ActiveTripActivity.EXTRA_PRICE, 0f);

        ((TextView) findViewById(R.id.text_driver_avatar)).setText(initials);
        ((TextView) findViewById(R.id.text_with_driver))
                .setText(getString(R.string.finished_trip_with_driver, name));
        ((TextView) findViewById(R.id.text_total_amount))
                .setText(String.format(Locale.getDefault(), "$%.2f", price));

        setUpStars();

        btnSubmit = findViewById(R.id.btn_submit);
        btnSubmit.setOnClickListener(v -> submitRating());
    }

    private void submitRating() {
        if (rating == 0) {
            Toast.makeText(this, R.string.finished_trip_rating_required_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (rideId == null) {
            finish();
            return;
        }

        String comment = ((EditText) findViewById(R.id.input_comment)).getText().toString().trim();
        LoadingButtonHelper.setLoading(btnSubmit, true);
        tripRepository.rateRide(rideId, rating, comment.isEmpty() ? null : comment, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                finish();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnSubmit, false);
                Toast.makeText(FinishedTripActivity.this, R.string.finished_trip_rating_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
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
