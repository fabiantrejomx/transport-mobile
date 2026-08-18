package com.bng.drivo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.OtpSendCallback;
import com.bng.drivo.data.repository.OtpVerifyCallback;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.util.ValidationHelper;

/**
 * Login por teléfono + OTP (Firebase Auth, sin contraseña). No hay pantalla
 * de registro separada: el mismo código verificado crea la sesión tanto para
 * un número nuevo como uno existente.
 */
public class LoginActivity extends AppCompatActivity {

    private AuthRepository authRepository;

    private View groupPhone;
    private View groupCode;
    private EditText inputPhone;
    private EditText inputCode;
    private TextView textCodeSentTo;
    private View btnSendCode;
    private View btnVerifyCode;

    private String phoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authRepository = new FirebaseAuthRepository();

        groupPhone = findViewById(R.id.group_phone);
        groupCode = findViewById(R.id.group_code);
        inputPhone = findViewById(R.id.input_phone);
        inputCode = findViewById(R.id.input_code);
        textCodeSentTo = findViewById(R.id.text_code_sent_to);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnVerifyCode = findViewById(R.id.btn_verify_code);

        btnSendCode.setOnClickListener(v -> attemptSendCode());
        btnVerifyCode.setOnClickListener(v -> attemptVerifyCode());
        findViewById(R.id.link_change_phone).setOnClickListener(v -> showPhoneStep());
    }

    private void attemptSendCode() {
        phoneNumber = inputPhone.getText().toString().trim();

        if (!ValidationHelper.isValidPhone(phoneNumber)) {
            Toast.makeText(this, R.string.auth_login_empty_phone_error, Toast.LENGTH_SHORT).show();
            return;
        }

        setSendingEnabled(false);
        authRepository.sendVerificationCode(this, phoneNumber, new OtpSendCallback() {
            @Override
            public void onCodeSent(String verificationId) {
                setSendingEnabled(true);
                showCodeStep();
            }

            @Override
            public void onAutoVerified() {
                setSendingEnabled(true);
                goToHome();
            }

            @Override
            public void onError(String message) {
                setSendingEnabled(true);
                Toast.makeText(LoginActivity.this, R.string.auth_login_send_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attemptVerifyCode() {
        String code = inputCode.getText().toString().trim();

        if (!ValidationHelper.isNotEmpty(code)) {
            Toast.makeText(this, R.string.auth_login_empty_code_error, Toast.LENGTH_SHORT).show();
            return;
        }

        setVerifyingEnabled(false);
        authRepository.verifyCode(code, new OtpVerifyCallback() {
            @Override
            public void onSuccess() {
                setVerifyingEnabled(true);
                goToHome();
            }

            @Override
            public void onError(String message) {
                setVerifyingEnabled(true);
                Toast.makeText(LoginActivity.this, R.string.auth_login_verify_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCodeStep() {
        textCodeSentTo.setText(getString(R.string.auth_login_code_sent, phoneNumber));
        groupPhone.setVisibility(View.GONE);
        groupCode.setVisibility(View.VISIBLE);
    }

    private void showPhoneStep() {
        inputCode.setText("");
        groupCode.setVisibility(View.GONE);
        groupPhone.setVisibility(View.VISIBLE);
    }

    private void setSendingEnabled(boolean enabled) {
        btnSendCode.setEnabled(enabled);
    }

    private void setVerifyingEnabled(boolean enabled) {
        btnVerifyCode.setEnabled(enabled);
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
