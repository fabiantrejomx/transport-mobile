package com.bng.drivo.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.bng.drivo.R;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.util.LoadingButtonHelper;
import com.bng.drivo.util.ValidationHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

/** Editar nombre/correo — PATCH /me real, mismo repositorio que CompleteProfileActivity. */
public class EditProfileBottomSheet extends BottomSheetDialogFragment {

    public static void present(FragmentManager fragmentManager) {
        new EditProfileBottomSheet().show(fragmentManager, "edit_profile");
    }

    private UserRepository userRepository;
    private EditText inputName;
    private EditText inputEmail;
    private MaterialButton btnSave;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userRepository = new RestUserRepository(requireContext());
        inputName = view.findViewById(R.id.input_name);
        inputEmail = view.findViewById(R.id.input_email);
        btnSave = view.findViewById(R.id.btn_save_profile);

        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded()) {
                    return;
                }
                inputName.setText(profile.getName());
                inputEmail.setText(profile.getEmail());
            }

            @Override
            public void onError(ApiException error) {
                // El modal sigue usable con los campos vacíos; el usuario puede reintentar.
            }
        });

        btnSave.setOnClickListener(v -> attemptSave());
    }

    private void attemptSave() {
        String name = inputName.getText().toString().trim();
        String email = inputEmail.getText().toString().trim();

        if (!ValidationHelper.isNotEmpty(name)) {
            Toast.makeText(requireContext(), R.string.perfil_edit_empty_name_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!email.isEmpty() && !ValidationHelper.isValidEmail(email)) {
            Toast.makeText(requireContext(), R.string.perfil_edit_invalid_email_error, Toast.LENGTH_SHORT).show();
            return;
        }

        LoadingButtonHelper.setLoading(btnSave, true);
        userRepository.updateProfile(name, email.isEmpty() ? null : email, new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                if (isAdded()) {
                    dismiss();
                }
            }

            @Override
            public void onError(ApiException error) {
                if (!isAdded()) {
                    return;
                }
                LoadingButtonHelper.setLoading(btnSave, false);
                Toast.makeText(requireContext(), R.string.perfil_edit_save_error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
