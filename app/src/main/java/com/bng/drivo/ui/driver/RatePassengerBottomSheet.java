package com.bng.drivo.ui.driver;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.bng.drivo.R;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.util.ColorUtils;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La calificación al pasajero que quedó sin enviar cuando la app se cerró sobre el panel de cobro.
 *
 * <p>Ese panel vive en {@code DriverActiveTripActivity} y se pinta con la respuesta de
 * {@code POST /driver/rides/{id}/complete} — la única que trae la comisión. Cerrar la app se
 * llevaba las dos cosas: el cobro y la calificación. El cobro no se puede rehacer sin inventarse
 * la comisión (por eso {@code onStatusChanged} ya se negaba a fabricarlo), pero la calificación sí
 * se puede enviar en cualquier momento: el contrato solo exige que el viaje esté terminado.
 *
 * <p>Se ofrece una sola vez por arranque y solo del <b>último</b> viaje: perseguir al conductor con
 * calificaciones viejas cada vez que abre la app convierte una cortesía en un peaje.
 *
 * <p>"Ahora no" cierra sin enviar y sin castigo. La calificación sigue pendiente en el servidor y
 * se volverá a ofrecer en el siguiente arranque mientras siga siendo el último viaje.
 */
public class RatePassengerBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_RIDE_ID = "arg_ride_id";
    private static final String ARG_DEST_TEXT = "arg_dest_text";
    private static final String ARG_FARE = "arg_fare";

    private static final int STAR_COUNT = 5;

    private final List<TextView> starViews = new ArrayList<>();
    private int rating;
    private MaterialButton btnSubmit;

    public static void present(@NonNull FragmentManager fragmentManager, @NonNull RideSummary ride) {
        RatePassengerBottomSheet sheet = new RatePassengerBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_RIDE_ID, ride.getId());
        args.putString(ARG_DEST_TEXT, ride.getDestText());
        args.putDouble(ARG_FARE, ride.getAgreedFare() != null ? ride.getAgreedFare() : 0);
        sheet.setArguments(args);
        sheet.show(fragmentManager, "rate_passenger");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_rate_passenger, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            dismiss();
            return;
        }

        String destination = args.getString(ARG_DEST_TEXT);
        double fare = args.getDouble(ARG_FARE);
        String trip = getString(R.string.driver_pending_rating_destination, destination);
        if (fare > 0) {
            trip += String.format(Locale.getDefault(), " · $%.2f", fare);
        }
        ((TextView) view.findViewById(R.id.text_rate_passenger_trip)).setText(trip);

        setUpStars(view.findViewById(R.id.container_rate_passenger_stars));

        btnSubmit = view.findViewById(R.id.btn_rate_passenger_submit);
        btnSubmit.setEnabled(false);
        btnSubmit.setOnClickListener(v -> submit(args.getString(ARG_RIDE_ID)));
        view.findViewById(R.id.btn_rate_passenger_skip).setOnClickListener(v -> dismiss());
    }

    private void submit(@Nullable String rideId) {
        if (rideId == null || rating == 0) {
            dismiss();
            return;
        }
        LoadingButtonHelper.setLoading(btnSubmit, true);
        new RestDriverRepository(requireContext())
                .rateRide(rideId, rating, null, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        if (isAdded()) {
                            dismiss();
                        }
                    }

                    @Override
                    public void onError(ApiException error) {
                        if (!isAdded()) {
                            return;
                        }
                        LoadingButtonHelper.setLoading(btnSubmit, false);
                        Toast.makeText(requireContext(), R.string.driver_pending_rating_error,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Las mismas estrellas de texto que el resto de la app, con el mismo par de colores. */
    private void setUpStars(LinearLayout container) {
        int sizePx = (int) (8 * getResources().getDisplayMetrics().density);
        for (int i = 1; i <= STAR_COUNT; i++) {
            TextView star = new TextView(requireContext());
            star.setText("★");
            star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f);
            star.setPadding(sizePx, 0, sizePx, 0);
            int starIndex = i;
            star.setOnClickListener(v -> {
                rating = starIndex;
                btnSubmit.setEnabled(true);
                updateStars();
            });
            container.addView(star);
            starViews.add(star);
        }
        updateStars();
    }

    private void updateStars() {
        int selected = ColorUtils.resolveThemeColor(requireContext(),
                com.google.android.material.R.attr.colorSecondary);
        int unselected = ColorUtils.resolveThemeColor(requireContext(),
                com.google.android.material.R.attr.colorOutline);
        for (int i = 0; i < starViews.size(); i++) {
            starViews.get(i).setTextColor(i < rating ? selected : unselected);
        }
    }
}
