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
import com.bng.drivo.ui.driver.DriverEntryPoint;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.util.LoadingButtonHelper;
import com.bng.drivo.util.ProfileFieldLock;
import com.bng.drivo.util.ValidationHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Paso "completar perfil" tras el primer login: pide nombre (obligatorio) y correo (opcional) y
 * hace PATCH /me. Solo se llega aquí si /me ya vino sin nombre — ver
 * LoginActivity.syncProfileAndContinue() y SplashActivity.
 *
 * <p>Quien entra con Google normalmente no pasa por aquí, porque el nombre llega del propio token.
 * Puede pasar si su cuenta de Google no tiene nombre; en ese caso el correo ya está puesto y
 * bloqueado, y mandarlo sería un 403 EMAIL_LOCKED.
 */
public class CompleteProfileActivity extends AuthenticatedActivity {

    private UserRepository userRepository;
    private EditText inputName;
    private EditText inputEmail;
    private TextInputLayout layoutEmail;
    private MaterialButton btnSave;

    /** Null hasta que responde /me; hasta entonces no se sabe qué campos se pueden mandar. */
    private UserProfile profile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_profile);

        userRepository = new RestUserRepository(this);
        inputName = findViewById(R.id.input_name);
        inputEmail = findViewById(R.id.input_email);
        layoutEmail = findViewById(R.id.layout_email);
        btnSave = findViewById(R.id.btn_save_profile);

        loadProfile();
        btnSave.setOnClickListener(v -> attemptSave());
    }

    private void loadProfile() {
        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                profile = result;
                if (result.isGoogleAccount()) {
                    ProfileFieldLock.lockEmail(layoutEmail, result.getEmail());
                }
            }

            @Override
            public void onError(ApiException error) {
                // La pantalla sigue usable: sin respuesta se trata como cuenta de teléfono, que
                // es lo que llega aquí en la práctica, y el servidor tiene la última palabra.
            }
        });
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

        // En una cuenta de Google el correo es de Google: mandarlo sería un 403 EMAIL_LOCKED, y
        // además no habría nada que guardar — el campo enseña justo lo que el servidor ya tiene.
        boolean emailBloqueado = profile != null && profile.isGoogleAccount();
        String emailAEnviar = emailBloqueado || email.isEmpty() ? null : email;

        LoadingButtonHelper.setLoading(btnSave, true);
        userRepository.updateProfile(name, emailAEnviar, null, new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                goToHome();
            }

            @Override
            public void onError(ApiException error) {
                LoadingButtonHelper.setLoading(btnSave, false);
                String detail = error.getMessage();
                String message = detail != null && !detail.isEmpty()
                        ? getString(R.string.complete_profile_save_error) + " (" + error.getCode() + ": " + detail + ")"
                        : getString(R.string.complete_profile_save_error);
                Toast.makeText(CompleteProfileActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goToHome() {
        boolean driverRole = getIntent().getBooleanExtra(LoginActivity.EXTRA_DRIVER_ROLE, false);
        if (driverRole) {
            DriverEntryPoint.route(this);
            return;
        }
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
