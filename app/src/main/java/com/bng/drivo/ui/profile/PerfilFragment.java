package com.bng.drivo.ui.profile;

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
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.MockAuthRepository;
import com.bng.drivo.data.repository.MockUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.address.AddressListActivity;
import com.bng.drivo.ui.auth.LoginActivity;

/**
 * Pestaña "Perfil": réplica de pPerfil() del prototipo. Vive como pestaña permanente de la
 * barra inferior (ver HomeActivity), no como Activity aparte — las secciones que sí necesitan
 * navegación en profundidad (direcciones guardadas, etc.) siguen siendo Activities.
 */
public class PerfilFragment extends Fragment {

    private AuthRepository authRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_perfil, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authRepository = new MockAuthRepository(requireContext());
        UserRepository userRepository = new MockUserRepository(requireContext());
        UserProfile profile = userRepository.getCurrentUser();

        if (profile != null) {
            ((TextView) view.findViewById(R.id.text_avatar)).setText(profile.getInitials());
            ((TextView) view.findViewById(R.id.text_name)).setText(profile.getName());
            ((TextView) view.findViewById(R.id.text_rating))
                    .setText(getString(R.string.perfil_rating_subtitle, profile.getRating()));
        }

        view.findViewById(R.id.row_addresses).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddressListActivity.class)));
        view.findViewById(R.id.row_payment_methods).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.nav_section_coming_soon, Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.row_notifications).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.nav_section_coming_soon, Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.row_appearance).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.nav_section_coming_soon, Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> logout());
    }

    private void logout() {
        authRepository.logout();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
