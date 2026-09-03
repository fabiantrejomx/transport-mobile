package com.bng.drivo.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.bng.drivo.R;
import com.bng.drivo.util.PrefsHelper;

/**
 * Primera pantalla tras el splash cuando no hay sesión: elegir si el registro/login es de
 * pasajero o de conductor. Mismo login por OTP para ambos roles (Me es un solo perfil de
 * usuario) — el extra EXTRA_DRIVER_ROLE hace que LoginActivity/CompleteProfileActivity
 * terminen en DriverHomeActivity en vez de Home, y esa Activity trae su propio gate por
 * GET /driver/application (registro pendiente vs. ya aprobado).
 *
 * PREF_KEY_DRIVER_MODE recuerda la elección localmente para que SplashActivity sepa a cuál
 * de las dos Home mandar quien ya tiene sesión — no hay endpoint de "modo actual" en el
 * contrato, y consultar GET /driver/application en cada arranque para todo pasajero que nunca
 * se registró como conductor sería una llamada de más sin motivo.
 */
public class RoleSelectionActivity extends AppCompatActivity {

    public static final String PREF_KEY_DRIVER_MODE = "driver_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        // Sin finish(): esta pantalla se queda en el back stack para que LoginActivity pueda
        // regresar aquí (botón atrás), por si el usuario tocó el rol equivocado. No hay riesgo de
        // volver a verla después de un login exitoso: goToHome()/goToCompleteProfile() limpian el
        // task entero con CLEAR_TASK.
        findViewById(R.id.btn_role_passenger).setOnClickListener(v -> {
            new PrefsHelper(this).putBoolean(PREF_KEY_DRIVER_MODE, false);
            startActivity(new Intent(this, LoginActivity.class));
        });

        findViewById(R.id.btn_role_driver).setOnClickListener(v -> {
            new PrefsHelper(this).putBoolean(PREF_KEY_DRIVER_MODE, true);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(LoginActivity.EXTRA_DRIVER_ROLE, true);
            startActivity(intent);
        });
    }
}
