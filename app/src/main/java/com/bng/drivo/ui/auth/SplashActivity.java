package com.bng.drivo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.driver.DriverEntryPoint;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.util.PrefsHelper;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 900L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AuthRepository authRepository;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        authRepository = new FirebaseAuthRepository();
        userRepository = new RestUserRepository(this);

        handler.postDelayed(this::goToNextScreen, SPLASH_DELAY_MS);
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

        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
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
                // Sin red no podemos saber si el perfil está completo; Home está protegido por
                // AuthenticatedActivity de todas formas.
                if (driverMode) {
                    DriverEntryPoint.route(SplashActivity.this);
                } else {
                    navigateTo(HomeActivity.class, false);
                }
            }
        });
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
