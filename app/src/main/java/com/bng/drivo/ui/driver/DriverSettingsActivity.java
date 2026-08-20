package com.bng.drivo.ui.driver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.R;
import com.bng.drivo.data.model.DriverApplication;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import com.bng.drivo.ui.auth.RoleSelectionActivity;
import com.bng.drivo.ui.settings.AppearanceBottomSheet;
import com.bng.drivo.ui.settings.SimpleMessageBottomSheet;

import java.util.List;

/**
 * C6: configuración del conductor. "Mi Vehículo" y "Wallet y Recargas" no tienen pantalla de
 * edición/recarga real todavía (el contrato no expone GET del vehículo ya guardado ni un
 * endpoint de autorecarga — ver plan de la Fase 7), así que quedan como avisos informativos
 * honestos en vez de simular una función que no existe. "Mis Documentos" sí es real: usa
 * required_documents/missing_documents de GET /driver/application, que ya trae el detalle.
 */
public class DriverSettingsActivity extends AuthenticatedActivity {

    private DriverRepository driverRepository;
    private UserRepository userRepository;
    private AuthRepository authRepository;

    private DriverApplication lastApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_settings);

        driverRepository = new RestDriverRepository(this);
        userRepository = new RestUserRepository(this);
        authRepository = new FirebaseAuthRepository();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        bindRow(R.id.row_vehicle, "🚗", R.string.driver_settings_row_vehicle, this::showVehicleInfo);
        bindRow(R.id.row_documents, "📄", R.string.driver_settings_row_documents, this::showDocumentsInfo);
        bindRow(R.id.row_wallet, "💰", R.string.driver_settings_row_wallet, this::showWalletInfo);
        bindRow(R.id.row_security, "🛡️", R.string.driver_settings_row_security,
                () -> startActivity(new Intent(this, DriverSecurityActivity.class)));
        bindRow(R.id.row_appearance, "🎨", R.string.driver_settings_row_appearance,
                () -> AppearanceBottomSheet.present(getSupportFragmentManager()));

        findViewById(R.id.btn_logout).setOnClickListener(v -> logout());

        loadProfile();
        loadApplication();
    }

    private void bindRow(int includeId, String icon, int labelRes, Runnable onClick) {
        View row = findViewById(includeId);
        ((TextView) row.findViewById(R.id.row_icon)).setText(icon);
        ((TextView) row.findViewById(R.id.row_label)).setText(labelRes);
        row.setOnClickListener(v -> onClick.run());
    }

    private void loadProfile() {
        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                ((TextView) findViewById(R.id.text_avatar)).setText(profile.getInitials());
                ((TextView) findViewById(R.id.text_name)).setText(profile.getName());
            }

            @Override
            public void onError(ApiException error) {
                Toast.makeText(DriverSettingsActivity.this, R.string.driver_settings_load_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void loadApplication() {
        driverRepository.getApplication(new ApiCallback<DriverApplication>() {
            @Override
            public void onSuccess(DriverApplication application) {
                lastApplication = application;
                TextView textStatus = findViewById(R.id.text_account_status);
                if ("approved".equals(application.getStatus())) {
                    textStatus.setText(R.string.driver_settings_account_active);
                    textStatus.setTextColor(getColor(R.color.drivo_success));
                } else if ("rejected".equals(application.getStatus())) {
                    textStatus.setText(R.string.driver_settings_account_status_rejected);
                    textStatus.setTextColor(getColor(R.color.drivo_error));
                } else if ("suspended".equals(application.getStatus())) {
                    textStatus.setText(R.string.driver_settings_account_status_suspended);
                    textStatus.setTextColor(getColor(R.color.drivo_error));
                } else if ("draft".equals(application.getStatus())) {
                    textStatus.setText(R.string.driver_settings_account_status_draft);
                    textStatus.setTextColor(getColor(R.color.drivo_secondary));
                } else {
                    textStatus.setText(R.string.driver_settings_account_status_pending_review);
                    textStatus.setTextColor(getColor(R.color.drivo_secondary));
                }
            }

            @Override
            public void onError(ApiException error) {
                // La cabecera de estado se queda vacía; el resto de la pantalla sigue usable.
            }
        });
    }

    private void showVehicleInfo() {
        SimpleMessageBottomSheet.present(getSupportFragmentManager(), getString(R.string.driver_settings_row_vehicle),
                getString(R.string.driver_settings_vehicle_message));
    }

    private void showDocumentsInfo() {
        String message;
        if (lastApplication == null) {
            message = getString(R.string.driver_settings_load_error);
        } else {
            List<String> missing = lastApplication.getMissingDocuments();
            message = missing == null || missing.isEmpty()
                    ? getString(R.string.driver_settings_documents_complete)
                    : getString(R.string.driver_settings_documents_missing_format, String.join(", ", missing));
        }
        SimpleMessageBottomSheet.present(getSupportFragmentManager(),
                getString(R.string.driver_settings_row_documents), message);
    }

    private void showWalletInfo() {
        SimpleMessageBottomSheet.present(getSupportFragmentManager(), getString(R.string.driver_settings_row_wallet),
                getString(R.string.driver_settings_wallet_message));
    }

    private void logout() {
        authRepository.logout();
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
