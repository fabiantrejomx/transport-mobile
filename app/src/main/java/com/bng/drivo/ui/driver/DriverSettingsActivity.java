package com.bng.drivo.ui.driver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.R;
import com.bng.drivo.data.model.DriverApplication;
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
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;
import java.util.Locale;

/**
 * C6: configuración del conductor — identidad, desempeño y preferencias. Es la contraparte de
 * {@code ConfiguracionesFragment} del pasajero y sigue su misma estructura a propósito (barra
 * superior, CUENTA, PREFERENCIAS, cerrar sesión); ver el comentario de activity_driver_settings.xml.
 *
 * <p>Dos datos que el contrato no regala y de dónde salen:
 * <ul>
 *   <li><b>Calificación:</b> el promedio lo calcula el backend. Se toma de {@code /me} si viene, y
 *       si no, del rodeo de {@link DriverRatingLoader}. Sin ninguna de las dos se queda en "—" en
 *       vez de inventar un 5.0.</li>
 *   <li><b>Viajes completados:</b> se cuentan de GET /rides?role=driver.</li>
 * </ul>
 *
 * <p>"Wallet y Recargas" es informativo: el contrato no expone ningún endpoint de autorecarga.
 * Las filas de solicitud/vehículo/documentos vivieron aquí y se retiraron a propósito — eran
 * volcados de la copia local del registro ({@code SubmittedApplicationCache}) y de
 * required_documents, y no se va a invertir tiempo en esa parte por ahora.
 */
public class DriverSettingsActivity extends DriverSubScreenActivity {

    private DriverRepository driverRepository;
    private UserRepository userRepository;
    private AuthRepository authRepository;

    /** Suficientes para contar viajes completados sin traer el historial entero. */
    private static final int PERFORMANCE_HISTORY_LIMIT = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_settings);

        driverRepository = new RestDriverRepository(this);
        userRepository = new RestUserRepository(this);
        authRepository = new FirebaseAuthRepository();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> openDrawer());

        bindRow(R.id.row_wallet, "💰", R.string.driver_settings_row_wallet, this::showWalletInfo);
        bindRow(R.id.row_security, "🛡️", R.string.perfil_security,
                () -> startActivity(new Intent(this, DriverSecurityActivity.class)));
        // Los mismos emojis que usa el pasajero para estas dos filas: es la misma preferencia.
        bindRow(R.id.row_appearance, "🌓", R.string.perfil_appearance,
                () -> AppearanceBottomSheet.present(getSupportFragmentManager()));
        bindRow(R.id.row_notifications, "🔔", R.string.perfil_notifications,
                () -> SimpleMessageBottomSheet.present(getSupportFragmentManager(),
                        getString(R.string.perfil_notifications),
                        getString(R.string.settings_notifications_coming_soon)));

        findViewById(R.id.btn_logout).setOnClickListener(v -> logout());

        loadProfile();
        loadApplication();
        loadPerformance();
    }

    @Override
    protected int navMenuItemId() {
        return R.id.nav_driver_settings;
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
                loadRating(profile.getRating());
            }

            @Override
            public void onError(ApiException error) {
                Toast.makeText(DriverSettingsActivity.this, R.string.driver_settings_load_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    /** Solo para la línea de estado bajo el nombre: es lo único que queda de la solicitud aquí. */
    private void loadApplication() {
        driverRepository.getApplication(new ApiCallback<DriverApplication>() {
            @Override
            public void onSuccess(DriverApplication application) {
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
            }

            @Override
            public void onError(ApiException error) {
                // Igual que el saldo: sin dato, no se pinta ninguno.
            }
        });
    }

    /**
     * El promedio sale de {@link DriverRatingLoader} — un rodeo por el historial de viajes que
     * desaparecerá en cuanto GET /me traiga el campo. Si el perfil ya lo trae, ni se llama.
     */
    private void loadRating(Double ratingFromProfile) {
        if (ratingFromProfile != null) {
            showRating(ratingFromProfile);
            return;
        }
        DriverRatingLoader.load(driverRepository, rating -> {
            // null deja el "—" del layout: un conductor sin viajes calificados todavía no tiene
            // promedio, y la línea de abajo ya explica de dónde sale ese número cuando lo haya.
            if (rating != null) {
                showRating(rating);
            }
        });
    }

    private void showRating(double rating) {
        ((TextView) findViewById(R.id.text_stat_rating))
                .setText(getString(R.string.rating_star_format, rating));
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
