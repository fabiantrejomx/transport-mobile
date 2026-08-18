package com.bng.drivo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.MockAuthRepository;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.util.ValidationHelper;

public class LoginActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private EditText inputEmail;
    private EditText inputPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authRepository = new MockAuthRepository(this);

        inputEmail = findViewById(R.id.input_email);
        inputPassword = findViewById(R.id.input_password);

        findViewById(R.id.btn_login).setOnClickListener(v -> attemptLogin());
        findViewById(R.id.link_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegistroActivity.class)));
    }

    private void attemptLogin() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString();

        if (!ValidationHelper.isNotEmpty(email) || !ValidationHelper.isNotEmpty(password)) {
            Toast.makeText(this, R.string.auth_login_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!authRepository.login(email, password)) {
            Toast.makeText(this, R.string.auth_login_error, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
