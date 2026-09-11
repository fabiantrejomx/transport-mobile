package com.bng.drivo.ui.trips;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bng.drivo.R;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.util.PlaceTextResolver;
import com.bng.drivo.util.RelativeDateFormatter;
import com.google.android.gms.maps.model.LatLng;

import java.util.Locale;

/** Infla y llena una fila de {@code item_trip_history.xml} — usado por ViajesFragment (historial
 * completo) y HomeFragment (sección "Últimos viajes" del modal), mismos datos reales. */
public final class TripHistoryRowBinder {

    private static final String STATUS_CANCELLED_PASSENGER = "CANCELLED_BY_PASSENGER";
    private static final String STATUS_CANCELLED_DRIVER = "CANCELLED_BY_DRIVER";
    private static final String STATUS_EXPIRED = "EXPIRED_NO_DRIVERS";

    private TripHistoryRowBinder() {
    }

    public static View addTrip(LayoutInflater inflater, LinearLayout container, RideSummary ride,
                                TripRepository tripRepository) {
        Context context = container.getContext();
        View row = inflater.inflate(R.layout.item_trip_history, container, false);

        ((TextView) row.findViewById(R.id.text_trip_date)).setText(RelativeDateFormatter.format(ride.getRequestedAt()));
        TextView originView = row.findViewById(R.id.text_trip_origin);
        originView.setText(ride.getOriginText());
        resolveOriginIfNeeded(context, ride, tripRepository, originView);
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

        // El destino que se muestra es el que se pidió, también cuando el viaje se cerró antes de
        // llegar: cambiarlo por donde acabó borraría del historial lo que el pasajero pidió. Lo
        // que corresponde es decirlo debajo, y marcar el punto de destino en amarillo para que se
        // distinga de un vistazo de los viajes que sí llegaron completos.
        row.findViewById(R.id.text_trip_ended_early)
                .setVisibility(ride.endedEarly() ? View.VISIBLE : View.GONE);
        row.findViewById(R.id.dot_trip_destination).setBackgroundTintList(ColorStateList.valueOf(
                context.getColor(ride.endedEarly() ? R.color.drivo_warning : R.color.drivo_error)));

        container.addView(row);
        return row;
    }

    /**
     * {@link RideSummary} (GET /rides) nunca trae coordenadas, solo el texto que se guardó —y ese
     * texto puede ser el placeholder "Tu ubicación actual" si el pasajero pidió ese viaje desde su
     * ubicación en curso. Aquí, visto en otro momento o desde otro lado, ese texto no dice nada.
     * Si hace falta, se pide el detalle completo (GET /rides/{id}, que sí trae originLat/Lng) solo
     * para esa fila y se geocodifica la coordenada real.
     */
    private static void resolveOriginIfNeeded(Context context, RideSummary ride,
                                               TripRepository tripRepository, TextView originView) {
        if (!PlaceTextResolver.isPlaceholder(context, ride.getOriginText())) {
            return;
        }
        tripRepository.getRideDetail(ride.getId(), new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride detail) {
                LatLng origin = detail.getOriginLat() != null && detail.getOriginLng() != null
                        ? new LatLng(detail.getOriginLat(), detail.getOriginLng()) : null;
                PlaceTextResolver.resolve(context, ride.getOriginText(), origin,
                        context.getString(R.string.trip_place_unknown), originView::setText);
            }

            @Override
            public void onError(ApiException error) {
                // Se queda el placeholder tal cual: no vale la pena romper la fila por esto.
            }
        });
    }

    private static boolean isCancelled(String status) {
        return STATUS_CANCELLED_PASSENGER.equals(status) || STATUS_CANCELLED_DRIVER.equals(status)
                || STATUS_EXPIRED.equals(status);
    }
}
