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
import com.google.android.material.appbar.MaterialToolbar;

public class RegistroActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private EditText inputName;
    private EditText inputEmail;
    private EditText inputPhone;
    private EditText inputPassword;
    private EditText inputConfirmPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        authRepository = new MockAuthRepository(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        inputName = findViewById(R.id.input_name);
        inputEmail = findViewById(R.id.input_email);
        inputPhone = findViewById(R.id.input_phone);
        inputPassword = findViewById(R.id.input_password);
        inputConfirmPassword = findViewById(R.id.input_confirm_password);

        findViewById(R.id.btn_register).setOnClickListener(v -> attemptRegister());
        findViewById(R.id.link_login).setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String name = inputName.getText().toString().trim();
        String email = inputEmail.getText().toString().trim();
        String phone = inputPhone.getText().toString().trim();
        String password = inputPassword.getText().toString();
        String confirmPassword = inputConfirmPassword.getText().toString();

        if (!ValidationHelper.isNotEmpty(name) || !ValidationHelper.isNotEmpty(password)) {
            Toast.makeText(this, R.string.auth_registro_error_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ValidationHelper.isValidEmail(email)) {
            Toast.makeText(this, R.string.auth_registro_error_email, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ValidationHelper.isValidPhone(phone)) {
            Toast.makeText(this, R.string.auth_registro_error_phone, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ValidationHelper.passwordsMatch(password, confirmPassword)) {
            Toast.makeText(this, R.string.auth_registro_error_password_match, Toast.LENGTH_SHORT).show();
            return;
        }

        authRepository.register(name, email, phone, password);

        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
