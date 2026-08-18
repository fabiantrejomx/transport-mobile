package com.bng.drivo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.bng.drivo.R;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.util.ValidationHelper;

/**
 * Paso "completar perfil" tras el primer login OTP: pide nombre (obligatorio) y correo
 * (opcional) y hace PATCH /me. Solo se llega aquí si /me ya vino sin nombre — ver
 * LoginActivity.syncProfileAndContinue() y SplashActivity.
 */
public class CompleteProfileActivity extends AuthenticatedActivity {

    private UserRepository userRepository;
    private EditText inputName;
    private EditText inputEmail;
    private View btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_profile);

        userRepository = new RestUserRepository(this);
        inputName = findViewById(R.id.input_name);
        inputEmail = findViewById(R.id.input_email);
        btnSave = findViewById(R.id.btn_save_profile);

        btnSave.setOnClickListener(v -> attemptSave());
    }

    private void attemptSave() {
        String name = inputName.getText().toString().trim();
        String email = inputEmail.getText().toString().trim();

        if (!ValidationHelper.isNotEmpty(name)) {
            Toast.makeText(this, R.string.complete_profile_empty_name_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!email.isEmpty() && !ValidationHelper.isValidEmail(email)) {
            Toast.makeText(this, R.string.complete_profile_invalid_email_error, Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        userRepository.updateProfile(name, email.isEmpty() ? null : email, new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                goToHome();
            }

            @Override
            public void onError(ApiException error) {
                btnSave.setEnabled(true);
                String detail = error.getMessage();
                String message = detail != null && !detail.isEmpty()
                        ? getString(R.string.complete_profile_save_error) + " (" + error.getCode() + ": " + detail + ")"
                        : getString(R.string.complete_profile_save_error);
                Toast.makeText(CompleteProfileActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
