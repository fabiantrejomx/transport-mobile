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
import com.bng.drivo.ui.home.HomeActivity;

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
            navigateTo(LoginActivity.class);
            return;
        }

        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                navigateTo(profile.isComplete() ? HomeActivity.class : CompleteProfileActivity.class);
            }

            @Override
            public void onError(ApiException error) {
                // Sin red no podemos saber si el perfil está completo; Home está protegido por
                // AuthenticatedActivity de todas formas.
                navigateTo(HomeActivity.class);
            }
        });
    }

    private void navigateTo(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
