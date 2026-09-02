package com.bng.drivo.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bng.drivo.R;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AddressRepository;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.RestAddressRepository;
import com.bng.drivo.data.repository.RestTripRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.address.AddressListActivity;
import com.bng.drivo.ui.auth.RoleSelectionActivity;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.ui.security.SeguridadActivity;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

/**
 * "Configuración": fusiona lo que antes eran Perfil y Configuraciones en una sola pantalla
 * (identidad + Mis Lugares/Métodos de Pago/Seguridad + Apariencia/Notificaciones + cerrar
 * sesión). Los ajustes simples abren en modales tipo card (BottomSheetDialogFragment); Mis
 * Lugares y Seguridad se quedan como pantallas completas (ya tienen su propio flujo).
 */
public class ConfiguracionesFragment extends Fragment {

    /** De sobra para contar los viajes de un pasajero del piloto sin traer el historial entero. */
    private static final int TRIP_COUNT_LIMIT = 100;

    private AuthRepository authRepository;
    private UserRepository userRepository;
    private AddressRepository addressRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configuraciones, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authRepository = new FirebaseAuthRepository();
        userRepository = new RestUserRepository(requireContext());
        addressRepository = new RestAddressRepository(requireContext());

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> ((HomeActivity) requireActivity()).openDrawer());

        loadProfile(view);
        loadAddressCount(view);
        loadTripCount(view);

        view.findViewById(R.id.btn_edit_profile).setOnClickListener(v ->
                EditProfileBottomSheet.present(getChildFragmentManager()));
        view.findViewById(R.id.row_addresses).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddressListActivity.class)));
        view.findViewById(R.id.row_payment_methods).setOnClickListener(v ->
                SimpleMessageBottomSheet.present(getChildFragmentManager(),
                        getString(R.string.perfil_payment_methods), getString(R.string.settings_payment_coming_soon)));
        view.findViewById(R.id.row_security).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SeguridadActivity.class)));
        view.findViewById(R.id.row_appearance).setOnClickListener(v ->
                AppearanceBottomSheet.present(getChildFragmentManager()));
        view.findViewById(R.id.row_notifications).setOnClickListener(v ->
                SimpleMessageBottomSheet.present(getChildFragmentManager(),
                        getString(R.string.perfil_notifications), getString(R.string.settings_notifications_coming_soon)));
        view.findViewById(R.id.btn_logout).setOnClickListener(v -> logout());
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresca por si se editó el perfil o direcciones en otra pantalla/modal.
        if (getView() != null) {
            loadProfile(getView());
            loadAddressCount(getView());
            loadTripCount(getView());
        }
    }

    private void loadProfile(View view) {
        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded()) {
                    return;
                }
                ((TextView) view.findViewById(R.id.text_avatar)).setText(profile.getInitials());
                ((TextView) view.findViewById(R.id.text_name)).setText(profile.getName());
                ((TextView) view.findViewById(R.id.text_phone)).setText(profile.getPhone());
                showRating(view, profile.getRating());
            }

            @Override
            public void onError(ApiException error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.perfil_load_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadAddressCount(View view) {
        addressRepository.getAll(new ApiCallback<List<SavedAddress>>() {
            @Override
            public void onSuccess(List<SavedAddress> addresses) {
                if (isAdded()) {
                    showAddressCount(view, addresses.size());
                }
            }

            @Override
            public void onError(ApiException error) {
                // Sin respuesta no se escribe un cero: antes la fila decía "(0 guardados)" cuando
                // solo se había caído la red, y eso es afirmar que no tienes lugares. El renglón
                // se queda como "Mis Lugares" y sigue abriendo la pantalla, que es lo que importa.
                if (isAdded()) {
                    showAddressCount(view, 0);
                }
            }
        });
    }

    /** Cero y "no se sabe" se dibujan igual: sin número. Ver el comentario del layout. */
    private void showAddressCount(View view, int count) {
        TextView badge = view.findViewById(R.id.text_addresses_count);
        badge.setText(String.valueOf(count));
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * La calificación es la que manda {@code /me}. Si llega null se queda el guion del layout: el
     * backend todavía puede no exponer el campo, y un 5.0 inventado sería peor que un "sin dato".
     */
    private void showRating(View view, Double rating) {
        if (rating == null) {
            return;
        }
        ((TextView) view.findViewById(R.id.text_stat_rating))
                .setText(getString(R.string.rating_star_format, rating));
    }

    /**
     * Viajes completados contados del historial, y no el {@code trips} de {@code /me}: ese campo
     * cuenta calificaciones recibidas, que es otra cosa — quien viajó y nadie lo calificó sigue en
     * cero. Es además el mismo criterio que usa la Configuración del conductor, así que las dos
     * pantallas no pueden contradecirse.
     */
    private void loadTripCount(View view) {
        new RestTripRepository(requireContext()).getRideHistory(TRIP_COUNT_LIMIT,
                new ApiCallback<List<RideSummary>>() {
                    @Override
                    public void onSuccess(List<RideSummary> rides) {
                        if (!isAdded()) {
                            return;
                        }
                        int completed = 0;
                        for (RideSummary ride : rides) {
                            if ("COMPLETED".equals(ride.getStatus())) {
                                completed++;
                            }
                        }
                        ((TextView) view.findViewById(R.id.text_stat_trips))
                                .setText(String.valueOf(completed));
                    }

                    @Override
                    public void onError(ApiException error) {
                        // Se queda el guion: mejor "no lo sé" que un cero que parece un hecho.
                    }
                });
    }

    private void logout() {
        authRepository.logout();
        Intent intent = new Intent(requireContext(), RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
