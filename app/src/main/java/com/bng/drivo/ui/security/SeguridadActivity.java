package com.bng.drivo.ui.security;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

import com.bng.drivo.R;

/**
 * Centro de Seguridad: acceso rápido al 911 (marcador, no llamada automática — evita pedir el
 * permiso peligroso CALL_PHONE solo para esto) y accesos de "asistencia rápida" sin backend
 * real todavía (no hay endpoint de soporte/tickets en el contrato). Distinto del S.O.S. real
 * de ActiveTripActivity, que sí llama a POST /sos con el rideId de un viaje en curso — aquí no
 * hay necesariamente un viaje activo.
 */
public class SeguridadActivity extends AuthenticatedActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seguridad);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_call_911).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))));

        findViewById(R.id.row_lost_item).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.row_billing_issue).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.row_driver_mismatch).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.row_other_help).setOnClickListener(v -> showComingSoon());
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.security_help_coming_soon, Toast.LENGTH_LONG).show();
    }
}
