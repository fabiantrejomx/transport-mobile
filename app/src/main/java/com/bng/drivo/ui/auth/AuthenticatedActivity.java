package com.bng.drivo.ui.auth;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;

/**
 * Base de toda pantalla que requiere sesión iniciada. Revalida en cada
 * onStart (no solo onCreate) para cubrir también el caso de volver del
 * background tras un logout.
 */
public abstract class AuthenticatedActivity extends AppCompatActivity {

    private final AuthRepository authRepository = new FirebaseAuthRepository();

    @Override
    protected void onStart() {
        super.onStart();
        if (!authRepository.isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}
