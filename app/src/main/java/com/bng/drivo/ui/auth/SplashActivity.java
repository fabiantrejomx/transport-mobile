package com.bng.drivo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bng.drivo.R;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.ConnectivityRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.SystemConnectivityRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.driver.DriverEntryPoint;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.util.PrefsHelper;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 900L;
    private static final long PROFILE_FETCH_TIMEOUT_SECONDS = 5L;
    private static final long CIRCLE_ENTER_MS = 400L;
    private static final long CIRCLE_STAGGER_MS = 80L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AuthRepository authRepository;
    private UserRepository userRepository;
    private ConnectivityRepository connectivityRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // StatusBarStyler (en super.onCreate) pone íconos oscuros en tema claro, pero el splash es
        // verde noche en los dos temas: sin esto la hora y la batería no se verían.
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(false);
        insetsController.setAppearanceLightNavigationBars(false);
        setContentView(R.layout.activity_splash);
        animateCirclesIn();

        authRepository = new FirebaseAuthRepository();
        userRepository = new RestUserRepository(this);
        connectivityRepository = new SystemConnectivityRepository(this);

        handler.postDelayed(this::goToNextScreen, SPLASH_DELAY_MS);
    }

    /**
     * En Android 12+ el splash del sistema, que va antes que este, solo puede llevar el logo: los
     * círculos de las esquinas aparecen hasta que esta pantalla pinta. En vez de que salgan de
     * golpe, crecen desde su esquina, así que el relevo se lee como una entrada y no como un salto.
     * El pivote por omisión (el centro de cada círculo) cae justo en su esquina, que es de donde
     * tienen que salir. Con las animaciones del sistema apagadas, el animador termina al instante.
     */
    private void animateCirclesIn() {
        // Curva "emphasized decelerate" de Material 3 (tokens drivo-easing-emphasized-decelerate).
        PathInterpolator emphasizedDecelerate = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        growFromCorner(findViewById(R.id.circle_splash_green), 0L, emphasizedDecelerate);
        growFromCorner(findViewById(R.id.circle_splash_yellow), CIRCLE_STAGGER_MS, emphasizedDecelerate);
    }

    private static void growFromCorner(View circle, long delayMs, PathInterpolator interpolator) {
        circle.setScaleX(0f);
        circle.setScaleY(0f);
        circle.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delayMs)
                .setDuration(CIRCLE_ENTER_MS)
                .setInterpolator(interpolator);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void goToNextScreen() {
        if (!authRepository.isLoggedIn()) {
            navigateTo(RoleSelectionActivity.class, false);
            return;
        }

        boolean driverMode = new PrefsHelper(this).getBoolean(RoleSelectionActivity.PREF_KEY_DRIVER_MODE, false);

        if (!connectivityRepository.isOnline()) {
            // Sin conexión validada no tiene caso esperar el timeout de red: se entra igual y
            // Home ya sabe mostrar su banner de "sin conexión" en cuanto monte.
            proceedWithoutProfile(driverMode);
            return;
        }

        userRepository.getCurrentUser(PROFILE_FETCH_TIMEOUT_SECONDS, new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!profile.isComplete()) {
                    navigateTo(CompleteProfileActivity.class, driverMode);
                } else if (driverMode) {
                    // Mismo gate que tras el login (DriverEntryPoint): un conductor que cerró
                    // la app antes de terminar su registro no debe reabrir en DriverHomeActivity
                    // ni de forma transitoria — GET /driver/application decide entre esa y
                    // DriverRegistrationActivity en cada arranque, no solo la primera vez.
                    DriverEntryPoint.route(SplashActivity.this);
                } else {
                    navigateTo(HomeActivity.class, false);
                }
            }

            @Override
            public void onError(ApiException error) {
                proceedWithoutProfile(driverMode);
            }
        });
    }

    private void proceedWithoutProfile(boolean driverMode) {
        // Sin red no podemos saber si el perfil está completo; Home está protegido por
        // AuthenticatedActivity de todas formas.
        if (driverMode) {
            DriverEntryPoint.route(SplashActivity.this);
        } else {
            navigateTo(HomeActivity.class, false);
        }
    }

    /** {@code driverMode} solo importa para CompleteProfileActivity — el resto de destinos ya
     * están fijados a un rol y no leen el extra. */
    private void navigateTo(Class<?> destination, boolean driverMode) {
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(LoginActivity.EXTRA_DRIVER_ROLE, driverMode);
        startActivity(intent);
        finish();
    }
}
