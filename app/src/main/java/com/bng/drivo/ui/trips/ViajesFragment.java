package com.bng.drivo.ui.trips;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bng.drivo.R;

/**
 * "Tus viajes": réplica de pViajes() del prototipo. Historial de muestra estático —
 * el matching a viajes reales llegará cuando el flujo de solicitud se conecte a
 * Firestore (fuera de alcance de esta pasada, ver docs/drivo-analisis-inicial.md).
 * Vive como pestaña permanente de la barra inferior (ver HomeActivity), no como Activity aparte.
 */
public class ViajesFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_viajes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout container = view.findViewById(R.id.container_trips);
        if (container.getChildCount() > 0) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        addTrip(inflater, container, "Parque Los Fuertes", "Hoy, 10:32 am", "$68");
        addTrip(inflater, container, "Terminal de autobuses", "Ayer, 6:15 pm", "$54");
        addTrip(inflater, container, "Plaza Cristal", "Lun, 9:02 am", "$41");
    }

    private void addTrip(LayoutInflater inflater, LinearLayout container,
                          String destination, String date, String amount) {
        View row = inflater.inflate(R.layout.item_trip_history, container, false);
        ((TextView) row.findViewById(R.id.text_trip_destination)).setText(destination);
        ((TextView) row.findViewById(R.id.text_trip_date)).setText(date);
        ((TextView) row.findViewById(R.id.text_trip_amount)).setText(amount);
        container.addView(row);
    }
}
