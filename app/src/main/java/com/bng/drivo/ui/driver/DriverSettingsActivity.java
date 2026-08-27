package com.bng.drivo.ui.driver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.R;
import com.bng.drivo.data.model.DriverApplication;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.model.Wallet;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.auth.RoleSelectionActivity;
import com.bng.drivo.ui.settings.AppearanceBottomSheet;
import com.bng.drivo.ui.settings.SimpleMessageBottomSheet;
import com.bng.drivo.util.SubmittedApplicationCache;

import java.util.List;
import java.util.Locale;

/**
 * C6: configuración del conductor — identidad, desempeño y todo lo que mandó a revisión.
 *
 * <p>Tres datos que el contrato no regala y de dónde salen:
 * <ul>
 *   <li><b>Calificación:</b> no hay endpoint propio ni viene en /me. El bloque {@code driver} de
 *       GET /rides/{id} sí la trae, así que se lee del viaje más reciente de este conductor. Si
 *       todavía no tiene viajes, se queda en "—" en vez de inventar un 5.0.</li>
 *   <li><b>Viajes completados:</b> se cuentan de GET /rides?role=driver.</li>
 *   <li><b>Datos del vehículo/CURP/RFC:</b> ningún GET los devuelve. Se muestran desde la copia
 *       local que guarda el propio registro ({@link SubmittedApplicationCache}); si el registro
 *       se envió desde otro teléfono, se dice eso en vez de fingir que no existen.</li>
 * </ul>
 *
 * <p>"Mis Documentos" sí es 100% del servidor: required_documents/missing_documents de
 * GET /driver/application. "Wallet y Recargas" sigue siendo informativo — el contrato no expone
 * ningún endpoint de autorecarga.
 */
public class DriverSettingsActivity extends DriverSubScreenActivity {

    private DriverRepository driverRepository;
    private UserRepository userRepository;
    private AuthRepository authRepository;

    /** Suficientes para contar viajes completados sin traer el historial entero. */
    private static final int PERFORMANCE_HISTORY_LIMIT = 50;

    private DriverApplication lastApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_settings);

        driverRepository = new RestDriverRepository(this);
        userRepository = new RestUserRepository(this);
        authRepository = new FirebaseAuthRepository();

        findViewById(R.id.btn_back).setOnClickListener(v -> navigateHome());

        bindRow(R.id.row_application, "📝", R.string.driver_settings_row_application, this::showApplicationInfo);
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
        loadPerformance();
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

    /**
     * Calificación, viajes completados y saldo. Las tres piezas vienen de sitios distintos porque
     * el contrato no tiene un "resumen del conductor" — ver el javadoc de la clase.
     */
    private void loadPerformance() {
        driverRepository.getWallet(new ApiCallback<Wallet>() {
            @Override
            public void onSuccess(Wallet wallet) {
                ((TextView) findViewById(R.id.text_stat_balance)).setText(
                        String.format(Locale.getDefault(), "$%.2f", wallet.getBalance()));
            }

            @Override
            public void onError(ApiException error) {
                // Se queda el "—" del layout: mejor vacío que un cero que parece saldo real.
            }
        });

        driverRepository.getRideHistory(PERFORMANCE_HISTORY_LIMIT, new ApiCallback<List<RideSummary>>() {
            @Override
            public void onSuccess(List<RideSummary> rides) {
                int completed = 0;
                for (RideSummary ride : rides) {
                    if ("COMPLETED".equals(ride.getStatus())) {
                        completed++;
                    }
                }
                ((TextView) findViewById(R.id.text_stat_trips)).setText(String.valueOf(completed));
                if (!rides.isEmpty()) {
                    loadRatingFrom(rides.get(0).getId());
                }
            }

            @Override
            public void onError(ApiException error) {
                // Igual que el saldo: sin dato, no se pinta ninguno.
            }
        });
    }

    /** Único punto del contrato donde existe la calificación del propio conductor. */
    private void loadRatingFrom(String rideId) {
        driverRepository.getRideDetail(rideId, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (ride.getDriverRating() == null) {
                    return;
                }
                ((TextView) findViewById(R.id.text_stat_rating)).setText(
                        String.format(Locale.getDefault(), "★ %.1f", ride.getDriverRating()));
            }

            @Override
            public void onError(ApiException error) {
                // Sin calificación disponible se queda el "—".
            }
        });
    }

    /** Lo que el conductor envió a revisión, tal cual lo capturó: modalidad, CURP/RFC y estado. */
    private void showApplicationInfo() {
        StringBuilder message = new StringBuilder();
        if (lastApplication != null) {
            message.append(getString(R.string.driver_settings_application_status_label)).append(": ")
                    .append(statusLabel(lastApplication.getStatus())).append("\n");
            message.append(getString(R.string.driver_settings_application_modality_label)).append(": ")
                    .append(modalityLabel(lastApplication.getModality())).append("\n");
            String reason = lastApplication.getRejectionReason();
            if (reason != null && !reason.isEmpty()) {
                message.append("\n").append(getString(R.string.driver_settings_application_rejection_label))
                        .append(": ").append(reason).append("\n");
            }
        }

        SubmittedApplicationCache.Submitted submitted = SubmittedApplicationCache.read(this);
        if (submitted != null) {
            message.append("\n").append(getString(R.string.driver_settings_curp_label)).append(": ")
                    .append(submitted.curp).append("\n");
            if (submitted.rfc != null && !submitted.rfc.isEmpty()) {
                message.append(getString(R.string.driver_settings_rfc_label)).append(": ")
                        .append(submitted.rfc).append("\n");
            }
        }
        if (message.length() == 0) {
            message.append(getString(R.string.driver_settings_application_none));
        }
        SimpleMessageBottomSheet.present(getSupportFragmentManager(),
                getString(R.string.driver_settings_row_application), message.toString().trim());
    }

    /** El vehículo que se envió a revisión; ningún GET del contrato lo devuelve (ver la clase). */
    private void showVehicleInfo() {
        SubmittedApplicationCache.Submitted submitted = SubmittedApplicationCache.read(this);
        String message;
        if (submitted == null) {
            message = getString(R.string.driver_settings_vehicle_unknown);
        } else {
            message = submitted.brand + " " + submitted.model + " " + submitted.color + "\n"
                    + getString(R.string.driver_settings_vehicle_year_label) + ": " + submitted.year + "\n"
                    + getString(R.string.driver_settings_vehicle_plate_label) + ": " + submitted.plate + "\n\n"
                    + getString(submitted.isOwner ? R.string.driver_settings_vehicle_owner_yes
                            : R.string.driver_settings_vehicle_owner_no);
        }
        SimpleMessageBottomSheet.present(getSupportFragmentManager(),
                getString(R.string.driver_settings_row_vehicle), message);
    }

    private String statusLabel(String status) {
        if ("approved".equals(status)) {
            return getString(R.string.driver_settings_account_active);
        }
        if ("rejected".equals(status)) {
            return getString(R.string.driver_settings_account_status_rejected);
        }
        if ("suspended".equals(status)) {
            return getString(R.string.driver_settings_account_status_suspended);
        }
        if ("draft".equals(status)) {
            return getString(R.string.driver_settings_account_status_draft);
        }
        return getString(R.string.driver_settings_account_status_pending_review);
    }

    private String modalityLabel(String modality) {
        return "taxi".equals(modality)
                ? getString(R.string.driver_settings_application_modality_taxi)
                : getString(R.string.driver_settings_application_modality_particular);
    }

    /**
     * Lista completa con marca por documento en vez de una sola frase con los que faltan: así se
     * ve de un vistazo qué sí llegó, que es la mitad de la pregunta que se viene a contestar aquí.
     */
    private void showDocumentsInfo() {
        String message;
        if (lastApplication == null) {
            message = getString(R.string.driver_settings_load_error);
        } else {
            List<String> required = lastApplication.getRequiredDocuments();
            List<String> missing = lastApplication.getMissingDocuments();
            if (required == null || required.isEmpty()) {
                message = missing == null || missing.isEmpty()
                        ? getString(R.string.driver_settings_documents_complete)
                        : getString(R.string.driver_settings_documents_missing_format, String.join(", ", missing));
            } else {
                StringBuilder builder = new StringBuilder();
                for (String document : required) {
                    boolean pending = missing != null && missing.contains(document);
                    builder.append(getString(pending
                            ? R.string.driver_settings_application_doc_missing_format
                            : R.string.driver_settings_application_doc_ok_format, document)).append("\n");
                }
                message = builder.toString().trim();
            }
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
