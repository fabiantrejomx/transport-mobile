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
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.util.LoadingButtonHelper;
import com.bng.drivo.util.PhoneNumbers;
import com.bng.drivo.util.ProfileFieldLock;
import com.bng.drivo.util.ValidationHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Editar nombre, correo y teléfono — PATCH /me real, mismo repositorio que
 * CompleteProfileActivity.
 *
 * <p>Uno de los dos datos de contacto siempre está bloqueado: el que verificó el proveedor con el
 * que se creó la cuenta. Quien entró por SMS tiene el teléfono fijo y escribe su correo; quien
 * entró con Google, al revés. El candado se dibuja aquí y se aplica en el servidor — ver
 * {@link ProfileFieldLock}.
 */
public class EditProfileBottomSheet extends BottomSheetDialogFragment {

    public static void present(FragmentManager fragmentManager) {
        new EditProfileBottomSheet().show(fragmentManager, "edit_profile");
    }

    private UserRepository userRepository;
    private EditText inputName;
    private TextInputLayout layoutEmail;
    private TextInputLayout layoutPhone;
    private MaterialButton btnSave;

    /**
     * Null mientras /me no responda. Hasta entonces no se sabe qué campo está bloqueado, y por eso
     * el botón de guardar arranca apagado: mandar el campo equivocado es un 403.
     */
    private UserProfile profile;

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
        layoutEmail = view.findViewById(R.id.layout_email);
        layoutPhone = view.findViewById(R.id.layout_phone);
        btnSave = view.findViewById(R.id.btn_save_profile);

        btnSave.setEnabled(false);
        btnSave.setOnClickListener(v -> attemptSave());

        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                if (!isAdded()) {
                    return;
                }
                profile = result;
                bind(result);
                btnSave.setEnabled(true);
            }

            @Override
            public void onError(ApiException error) {
                if (isAdded()) {
                    // Sin saber cómo se dio de alta la cuenta no se puede ofrecer un formulario
                    // honesto: se avisa y se cierra en vez de dejar campos que quizá no guarden.
                    Toast.makeText(requireContext(), R.string.perfil_load_error, Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            }
        });
    }

    private void bind(UserProfile profile) {
        inputName.setText(profile.getName());

        if (profile.isGoogleAccount()) {
            ProfileFieldLock.lockEmail(layoutEmail, profile.getEmail());
            // El teléfono se escribe en local (10 dígitos) porque el campo ya lleva el prefijo
            // +52 dibujado; se vuelve a E.164 al guardar.
            editText(layoutPhone).setText(PhoneNumbers.toLocalDigits(profile.getPhone()));
        } else {
            editText(layoutEmail).setText(profile.getEmail());
            ProfileFieldLock.lockPhone(layoutPhone, PhoneNumbers.toLocalDigits(profile.getPhone()));
        }
    }

    private void attemptSave() {
        if (profile == null) {
            return;
        }
        String name = inputName.getText().toString().trim();
        if (!ValidationHelper.isNotEmpty(name)) {
            Toast.makeText(requireContext(), R.string.perfil_edit_empty_name_error, Toast.LENGTH_SHORT).show();
            return;
        }

        String email = null;
        String phone = null;

        if (profile.isGoogleAccount()) {
            String digits = editText(layoutPhone).getText().toString().trim();
            if (!digits.isEmpty()) {
                if (!ValidationHelper.isValidMexicanPhone(digits)) {
                    Toast.makeText(requireContext(), R.string.perfil_edit_invalid_phone_error,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                phone = PhoneNumbers.toE164(digits);
            }
        } else {
            String typed = editText(layoutEmail).getText().toString().trim();
            if (!typed.isEmpty() && !ValidationHelper.isValidEmail(typed)) {
                Toast.makeText(requireContext(), R.string.perfil_edit_invalid_email_error,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            email = typed.isEmpty() ? null : typed;
        }

        LoadingButtonHelper.setLoading(btnSave, true);
        userRepository.updateProfile(name, email, phone, new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.perfil_edit_save_success, Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            }

            @Override
            public void onError(ApiException error) {
                if (!isAdded()) {
                    return;
                }
                LoadingButtonHelper.setLoading(btnSave, false);
                // El número ocupado es el único error que el usuario puede resolver aquí mismo,
                // así que se dice tal cual en vez de esconderlo tras "no se pudo guardar".
                int message = error.getCode() == ApiErrorCode.PHONE_TAKEN
                        ? R.string.perfil_edit_phone_taken_error
                        : R.string.perfil_edit_save_error;
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private EditText editText(TextInputLayout layout) {
        return layout.getEditText();
    }
}
