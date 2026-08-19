package com.bng.drivo.ui.trips;

import android.os.Bundle;
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
import com.bng.drivo.util.RelativeDateFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

/** Detalle de un viaje del historial — GET /rides/{id} real, el único endpoint que sí trae
 * al conductor (la lista de GET /rides no lo incluye, ver ViajesFragment). */
public class TripDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_RIDE_ID = "arg_ride_id";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED_PASSENGER = "CANCELLED_BY_PASSENGER";
    private static final String STATUS_CANCELLED_DRIVER = "CANCELLED_BY_DRIVER";
    private static final String STATUS_EXPIRED = "EXPIRED_NO_DRIVERS";

    public static void present(FragmentManager fragmentManager, String rideId) {
        TripDetailBottomSheet sheet = new TripDetailBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_RIDE_ID, rideId);
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

        String rideId = getArguments() != null ? getArguments().getString(ARG_RIDE_ID) : null;
        if (rideId == null) {
            dismiss();
            return;
        }

        TripRepository tripRepository = new RestTripRepository(requireContext());
        tripRepository.getRideDetail(rideId, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (isAdded()) {
                    bindRide(view, ride);
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

    private void bindRide(View view, Ride ride) {
        view.findViewById(R.id.progress_detail).setVisibility(View.GONE);
        view.findViewById(R.id.group_detail_content).setVisibility(View.VISIBLE);

        ((TextView) view.findViewById(R.id.text_detail_date)).setText(RelativeDateFormatter.format(ride.getRequestedAt()));
        ((TextView) view.findViewById(R.id.text_detail_origin)).setText(ride.getOriginText());
        ((TextView) view.findViewById(R.id.text_detail_destination)).setText(ride.getDestinationText());
        ((TextView) view.findViewById(R.id.text_detail_status)).setText(statusLabel(ride.getStatus()));

        TextView amountView = view.findViewById(R.id.text_detail_amount);
        if (ride.getAgreedFare() != null) {
            amountView.setText(String.format(Locale.getDefault(), "$%.2f", ride.getAgreedFare()));
            amountView.setVisibility(View.VISIBLE);
        } else {
            amountView.setVisibility(View.GONE);
        }

        View driverGroup = view.findViewById(R.id.group_detail_driver);
        if (ride.getDriverName() != null) {
            driverGroup.setVisibility(View.VISIBLE);
            String vehicle = ride.getVehicleBrand() != null ? ride.getVehicleBrand() + " " + ride.getVehicleModel() : "";
            ((TextView) view.findViewById(R.id.text_detail_driver_avatar)).setText(initialsFor(ride.getDriverName()));
            ((TextView) view.findViewById(R.id.text_detail_driver))
                    .setText(getString(R.string.trip_detail_driver_format, ride.getDriverName(), vehicle));
        } else {
            driverGroup.setVisibility(View.GONE);
        }
    }

    private String statusLabel(String status) {
        if (STATUS_COMPLETED.equals(status)) {
            return getString(R.string.trip_status_completed);
        }
        if (STATUS_CANCELLED_PASSENGER.equals(status) || STATUS_CANCELLED_DRIVER.equals(status)) {
            return getString(R.string.trip_status_cancelled);
        }
        if (STATUS_EXPIRED.equals(status)) {
            return getString(R.string.trip_status_expired);
        }
        return getString(R.string.trip_status_in_progress);
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
}
