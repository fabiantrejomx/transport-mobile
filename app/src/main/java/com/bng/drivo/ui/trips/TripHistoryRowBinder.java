package com.bng.drivo.ui.trips;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bng.drivo.R;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.util.RelativeDateFormatter;

import java.util.Locale;

/** Infla y llena una fila de {@code item_trip_history.xml} — usado por ViajesFragment (historial
 * completo) y HomeFragment (sección "Últimos viajes" del modal), mismos datos reales. */
public final class TripHistoryRowBinder {

    private static final String STATUS_CANCELLED_PASSENGER = "CANCELLED_BY_PASSENGER";
    private static final String STATUS_CANCELLED_DRIVER = "CANCELLED_BY_DRIVER";
    private static final String STATUS_EXPIRED = "EXPIRED_NO_DRIVERS";

    private TripHistoryRowBinder() {
    }

    public static View addTrip(LayoutInflater inflater, LinearLayout container, RideSummary ride) {
        Context context = container.getContext();
        View row = inflater.inflate(R.layout.item_trip_history, container, false);

        ((TextView) row.findViewById(R.id.text_trip_date)).setText(RelativeDateFormatter.format(ride.getRequestedAt()));
        ((TextView) row.findViewById(R.id.text_trip_origin)).setText(ride.getOriginText());
        ((TextView) row.findViewById(R.id.text_trip_destination)).setText(ride.getDestText());

        TextView amountView = row.findViewById(R.id.text_trip_amount);
        if (isCancelled(ride.getStatus())) {
            amountView.setText(R.string.trip_status_cancelled);
            amountView.setTextColor(context.getColor(R.color.drivo_error));
        } else {
            double amount = ride.getAgreedFare() != null ? ride.getAgreedFare() : 0;
            amountView.setText(String.format(Locale.getDefault(), "$ %.2f", amount));
            amountView.setTextColor(context.getColor(R.color.drivo_success));
        }

        container.addView(row);
        return row;
    }

    private static boolean isCancelled(String status) {
        return STATUS_CANCELLED_PASSENGER.equals(status) || STATUS_CANCELLED_DRIVER.equals(status)
                || STATUS_EXPIRED.equals(status);
    }
}
