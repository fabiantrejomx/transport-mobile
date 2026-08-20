package com.bng.drivo.ui.driver;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.R;
import com.bng.drivo.ui.auth.AuthenticatedActivity;

/**
 * C8: Centro de Seguridad general del conductor (sin viaje activo) — mismo criterio que
 * SeguridadActivity del pasajero: marcador al 911, no llamada automática. El S.O.S. real
 * (POST /sos con rideId) solo tiene sentido durante un viaje y ya vive en
 * DriverActiveTripActivity — ver el hallazgo 6 del plan de la Fase 7.
 */
public class DriverSecurityActivity extends AuthenticatedActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_security);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_call_911).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))));

        bindRow(R.id.row_accident, "🚗", R.string.driver_security_row_accident);
        bindRow(R.id.row_aggressive_passenger, "🙅", R.string.driver_security_row_aggressive_passenger);
        bindRow(R.id.row_balance_issue, "💳", R.string.driver_security_row_balance_issue);
        bindRow(R.id.row_call_support, "🎧", R.string.driver_security_row_call_support);
    }

    private void bindRow(int includeId, String icon, int labelRes) {
        View row = findViewById(includeId);
        ((TextView) row.findViewById(R.id.row_icon)).setText(icon);
        ((TextView) row.findViewById(R.id.row_label)).setText(labelRes);
        row.setOnClickListener(v -> showComingSoon());
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.security_help_coming_soon, Toast.LENGTH_LONG).show();
    }
}
