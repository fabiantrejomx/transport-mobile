package com.bng.drivo.ui.trips;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.bng.drivo.R;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.ui.search.SearchingPanel;
import com.bng.drivo.util.ColorUtils;
import com.bng.drivo.util.NavHeaderRating;
import com.bng.drivo.util.PlaceTextResolver;
import com.bng.drivo.util.RelativeDateFormatter;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

/** Detalle de un viaje del historial — GET /rides/{id} real, el único endpoint que sí trae
 * al conductor (la lista de GET /rides no lo incluye, ver ViajesFragment). */
public class TripDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_RIDE_ID = "arg_ride_id";
    private static final String ARG_MY_RATING = "arg_my_rating";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED_PASSENGER = "CANCELLED_BY_PASSENGER";
    private static final String STATUS_CANCELLED_DRIVER = "CANCELLED_BY_DRIVER";
    private static final String STATUS_EXPIRED = "EXPIRED_NO_DRIVERS";

    /**
     * @param myRating las estrellas que el pasajero ya le puso a este viaje, o null si no lo ha
     *                 calificado. Viene del renglón de la lista ({@code RideSummary.myRating}):
     *                 GET /rides/{id} no lo trae, así que hay que cargarlo desde donde sí está en
     *                 vez de pedirlo dos veces.
     */
    public static void present(FragmentManager fragmentManager, String rideId, @Nullable Integer myRating) {
        TripDetailBottomSheet sheet = new TripDetailBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_RIDE_ID, rideId);
        args.putSerializable(ARG_MY_RATING, myRating);
        sheet.setArguments(args);
        sheet.show(fragmentManager, "trip_detail");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_trip_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String rideId = args != null ? args.getString(ARG_RIDE_ID) : null;
        if (rideId == null) {
            dismiss();
            return;
        }
        Integer myRating = args != null ? (Integer) args.getSerializable(ARG_MY_RATING) : null;

        TripRepository tripRepository = new RestTripRepository(requireContext());
        tripRepository.getRideDetail(rideId, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (isAdded()) {
                    bindRide(view, ride, myRating);
                }
            }

            @Override
            public void onError(ApiException error) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), R.string.trip_detail_load_error, Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });
    }

    private void bindRide(View view, Ride ride, @Nullable Integer myRating) {
        view.findViewById(R.id.progress_detail).setVisibility(View.GONE);
        view.findViewById(R.id.group_detail_content).setVisibility(View.VISIBLE);

        ((TextView) view.findViewById(R.id.text_detail_done_at)).setText(RelativeDateFormatter.formatDoneAt(ride.getRequestedAt()));

        bindPlace(view.findViewById(R.id.text_detail_origin), ride.getOriginText(),
                ride.getOriginLat(), ride.getOriginLng());
        bindPlace(view.findViewById(R.id.text_detail_destination), ride.getDestinationText(),
                ride.getDestinationLat(), ride.getDestinationLng());

        // El punto y la nota van juntos: el amarillo por sí solo no dice nada la primera vez que
        // se ve, y la nota sin el punto pierde la marca que ya distingue estos viajes en la lista.
        view.findViewById(R.id.dot_detail_destination).setBackgroundTintList(ColorStateList.valueOf(
                requireContext().getColor(ride.endedEarly() ? R.color.drivo_warning : R.color.drivo_error)));
        view.findViewById(R.id.text_detail_ended_early)
                .setVisibility(ride.endedEarly() ? View.VISIBLE : View.GONE);

        bindStatusBadge(view, ride.getStatus(), ride.endedEarly());
        bindPrice(view, ride);
        bindDriver(view, ride);
        bindMyRating(view, myRating);
    }

    /** Badge de color por estado, para que se distinga de un vistazo y no se lea como texto de
     * relleno — el mismo criterio que ya usan las píldoras de "En curso"/"Mejor precio" en la
     * búsqueda y el viaje del conductor (ver panel_driver_trip.xml, item_driver_offer.xml). */
    private void bindStatusBadge(View view, String status, boolean endedEarly) {
        TextView badge = view.findViewById(R.id.text_detail_status);
        int backgroundRes;
        int textColorRes;
        if (STATUS_COMPLETED.equals(status) && endedEarly) {
            badge.setText(R.string.trip_status_ended_early);
            backgroundRes = R.drawable.bg_pill_warning;
            textColorRes = R.color.drivo_on_warning;
        } else if (STATUS_COMPLETED.equals(status)) {
            badge.setText(R.string.trip_status_completed);
            backgroundRes = R.drawable.bg_pill_success;
            textColorRes = R.color.drivo_on_success;
        } else if (STATUS_CANCELLED_PASSENGER.equals(status) || STATUS_CANCELLED_DRIVER.equals(status)
                || STATUS_EXPIRED.equals(status)) {
            badge.setText(STATUS_EXPIRED.equals(status)
                    ? R.string.trip_status_expired : R.string.trip_status_cancelled);
            backgroundRes = R.drawable.bg_pill_error;
            textColorRes = R.color.drivo_on_error;
        } else {
            badge.setText(R.string.trip_status_in_progress);
            backgroundRes = R.drawable.bg_pill_success;
            textColorRes = R.color.drivo_on_success;
        }
        badge.setBackgroundResource(backgroundRes);
        badge.setTextColor(requireContext().getColor(textColorRes));
    }

    private void bindMyRating(View view, @Nullable Integer myRating) {
        View group = view.findViewById(R.id.group_detail_my_rating);
        if (myRating == null) {
            group.setVisibility(View.GONE);
            return;
        }
        group.setVisibility(View.VISIBLE);
        ((TextView) view.findViewById(R.id.text_detail_my_rating)).setText(starsFor(myRating));
    }

    /** Mismo par de colores con el que se le puso la calificación al conductor (ver
     * RatePassengerBottomSheet/FinishedTripActivity): rellenas en colorSecondary, huecas en
     * colorOutline — y huecas de verdad, con el carácter de contorno y no solo atenuadas. */
    private CharSequence starsFor(int rating) {
        int filledColor = ColorUtils.resolveThemeColor(requireContext(),
                com.google.android.material.R.attr.colorSecondary);
        int emptyColor = ColorUtils.resolveThemeColor(requireContext(),
                com.google.android.material.R.attr.colorOutline);
        SpannableStringBuilder stars = new SpannableStringBuilder();
        for (int i = 1; i <= 5; i++) {
            boolean filled = i <= rating;
            stars.append(filled ? '★' : '☆');
            stars.setSpan(new ForegroundColorSpan(filled ? filledColor : emptyColor),
                    i - 1, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return stars;
    }

    /** Mismo problema que resuelve PlaceTextResolver para el conductor: si lo que se guardó es el
     * placeholder "Tu ubicación actual", aquí tampoco dice nada — se geocodifica la coordenada. */
    private void bindPlace(TextView target, @Nullable String text, @Nullable Double lat, @Nullable Double lng) {
        LatLng latLng = lat != null && lng != null ? new LatLng(lat, lng) : null;
        PlaceTextResolver.resolve(requireContext(), text, latLng,
                getString(R.string.trip_place_unknown), target::setText);
    }

    private void bindPrice(View view, Ride ride) {
        boolean hasOffer = ride.getPassengerOffer() != null;
        view.findViewById(R.id.row_detail_offer).setVisibility(hasOffer ? View.VISIBLE : View.GONE);
        if (hasOffer) {
            ((TextView) view.findViewById(R.id.text_detail_offer_amount))
                    .setText(String.format(Locale.getDefault(), "$%.2f", ride.getPassengerOffer()));
        }

        boolean hasFinalPrice = ride.getAgreedFare() != null;
        view.findViewById(R.id.row_detail_final_price).setVisibility(hasFinalPrice ? View.VISIBLE : View.GONE);
        if (hasFinalPrice) {
            ((TextView) view.findViewById(R.id.text_detail_amount))
                    .setText(String.format(Locale.getDefault(), "$%.2f", ride.getAgreedFare()));
        }
    }

    private void bindDriver(View view, Ride ride) {
        View driverGroup = view.findViewById(R.id.group_detail_driver);
        if (ride.getDriverName() == null) {
            driverGroup.setVisibility(View.GONE);
            return;
        }
        driverGroup.setVisibility(View.VISIBLE);

        ((TextView) driverGroup.findViewById(R.id.text_nav_avatar)).setText(SearchingPanel.initialsFor(ride.getDriverName()));
        NavHeaderRating.apply(driverGroup, ride.getDriverRating(), null);
        ((TextView) view.findViewById(R.id.text_detail_driver_name)).setText(ride.getDriverName());

        String vehicle = SearchingPanel.joinNonNull(" ",
                ride.getVehicleBrand(), ride.getVehicleModel(), ride.getVehicleColor());
        TextView vehicleView = view.findViewById(R.id.text_detail_driver_vehicle);
        vehicleView.setText(vehicle);
        vehicleView.setVisibility(vehicle.isEmpty() ? View.GONE : View.VISIBLE);

        TextView plateView = view.findViewById(R.id.text_detail_driver_plate);
        if (ride.getVehiclePlate() != null) {
            plateView.setText(getString(R.string.trip_detail_plate_format, ride.getVehiclePlate()));
            plateView.setVisibility(View.VISIBLE);
        } else {
            plateView.setVisibility(View.GONE);
        }
    }
}
