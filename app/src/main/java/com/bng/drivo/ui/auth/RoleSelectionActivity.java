package com.bng.drivo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bng.drivo.R;

/**
 * Primera pantalla tras el splash cuando no hay sesión: elegir si el registro/login es de
 * pasajero o de conductor. El registro de conductor (7 pasos, ver CLAUDE.md) todavía no está
 * construido — Fase 7, plan aparte — así que por ahora solo avisa que viene pronto.
 */
public class RoleSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        findViewById(R.id.btn_role_passenger).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        findViewById(R.id.btn_role_driver).setOnClickListener(v ->
                Toast.makeText(this, R.string.role_selection_driver_coming_soon, Toast.LENGTH_SHORT).show());
    }
}
