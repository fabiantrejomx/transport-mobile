package com.bng.drivo.ui.trips;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bng.drivo.R;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.ui.home.HomeActivity;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Collections;
import java.util.List;

/**
 * "Mis Viajes": historial real vía GET /rides (ver TripRepository.getRideHistory) — ya no es
 * el mock estático de antes. Cada fila abre TripDetailBottomSheet, que sí trae al conductor
 * (GET /rides/{id}, el único endpoint que lo incluye).
 */
public class ViajesFragment extends Fragment {

    private static final int HISTORY_LIMIT = 20;

    private TripRepository tripRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_viajes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tripRepository = new RestTripRepository(requireContext());

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> ((HomeActivity) requireActivity()).openDrawer());

        loadHistory(view);
    }

    private void loadHistory(View view) {
        tripRepository.getRideHistory(HISTORY_LIMIT, new ApiCallback<List<RideSummary>>() {
            @Override
            public void onSuccess(List<RideSummary> rides) {
                if (isAdded()) {
                    bindHistory(view, rides);
                }
            }

            @Override
            public void onError(ApiException error) {
                if (!isAdded()) {
                    return;
                }
                bindHistory(view, Collections.emptyList());
                Toast.makeText(requireContext(), R.string.viajes_load_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindHistory(View view, List<RideSummary> rides) {
        LinearLayout container = view.findViewById(R.id.container_trips);
        container.removeAllViews();
        view.findViewById(R.id.text_empty).setVisibility(rides.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (RideSummary ride : rides) {
            View row = TripHistoryRowBinder.addTrip(inflater, container, ride);
            row.setOnClickListener(v -> TripDetailBottomSheet.present(getParentFragmentManager(), ride.getId()));
        }
    }
}
