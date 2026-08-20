package com.bng.drivo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.OtpSendCallback;
import com.bng.drivo.data.repository.OtpVerifyCallback;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.driver.DriverEntryPoint;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.util.LoadingButtonHelper;
import com.bng.drivo.util.ValidationHelper;
import com.google.android.material.button.MaterialButton;

/**
 * Login por teléfono + OTP (Firebase Auth, sin contraseña). No hay pantalla
 * de registro separada: el mismo código verificado crea la sesión tanto para
 * un número nuevo como uno existente.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String PHONE_PREFIX = "+52";

    /** Puesto por RoleSelectionActivity cuando el login es para "Soy conductor". */
    public static final String EXTRA_DRIVER_ROLE = "driver_role";

    private AuthRepository authRepository;
    private UserRepository userRepository;
    private boolean driverRole;

    private View groupPhone;
    private View groupCode;
    private EditText inputPhone;
    private EditText[] codeDigits;
    private TextView textCodeSentTo;
    private MaterialButton btnSendCode;
    private MaterialButton btnVerifyCode;

    private String phoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authRepository = new FirebaseAuthRepository();
        userRepository = new RestUserRepository(this);
        driverRole = getIntent().getBooleanExtra(EXTRA_DRIVER_ROLE, false);

        groupPhone = findViewById(R.id.group_phone);
        groupCode = findViewById(R.id.group_code);
        inputPhone = findViewById(R.id.input_phone);
        codeDigits = new EditText[]{
                findViewById(R.id.input_code_0), findViewById(R.id.input_code_1),
                findViewById(R.id.input_code_2), findViewById(R.id.input_code_3),
                findViewById(R.id.input_code_4), findViewById(R.id.input_code_5)
        };
        textCodeSentTo = findViewById(R.id.text_code_sent_to);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnVerifyCode = findViewById(R.id.btn_verify_code);

        setUpPhoneInput();
        setUpCodeDigitInputs();
        btnSendCode.setOnClickListener(v -> attemptSendCode());
        btnVerifyCode.setOnClickListener(v -> attemptVerifyCode());
        findViewById(R.id.link_change_phone).setOnClickListener(v -> showPhoneStep());
    }

    /** Deshabilitado hasta que estén los 10 dígitos — la validación en el click se queda como
     * respaldo, pero el usuario nunca debería llegar a dispararla. */
    private void setUpPhoneInput() {
        btnSendCode.setEnabled(false);
        inputPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnSendCode.setEnabled(s.length() == 10);
            }
        });
    }

    private void attemptSendCode() {
        String digits = inputPhone.getText().toString().trim();

        if (!ValidationHelper.isValidMexicanPhone(digits)) {
            Toast.makeText(this, R.string.auth_login_empty_phone_error, Toast.LENGTH_SHORT).show();
            return;
        }
        phoneNumber = PHONE_PREFIX + digits;

        LoadingButtonHelper.setLoading(btnSendCode, true);
        authRepository.sendVerificationCode(this, phoneNumber, new OtpSendCallback() {
            @Override
            public void onCodeSent(String verificationId) {
                LoadingButtonHelper.setLoading(btnSendCode, false);
                showCodeStep();
            }

            @Override
            public void onAutoVerified() {
                LoadingButtonHelper.setLoading(btnSendCode, false);
                syncProfileAndContinue();
            }

            @Override
            public void onError(String message) {
                LoadingButtonHelper.setLoading(btnSendCode, false);
                Toast.makeText(LoginActivity.this, R.string.auth_login_send_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Deshabilitado hasta que los 6 dígitos estén escritos. */
    private void setUpCodeDigitInputs() {
        btnVerifyCode.setEnabled(false);
        for (int i = 0; i < codeDigits.length; i++) {
            int index = i;
            codeDigits[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && index < codeDigits.length - 1) {
                        codeDigits[index + 1].requestFocus();
                    }
                    btnVerifyCode.setEnabled(currentCode().length() == 6);
                }
            });
            codeDigits[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                        && codeDigits[index].getText().toString().isEmpty() && index > 0) {
                    codeDigits[index - 1].requestFocus();
                    codeDigits[index - 1].setText("");
                }
                return false;
            });
        }
    }

    private String currentCode() {
        StringBuilder code = new StringBuilder();
        for (EditText digit : codeDigits) {
            code.append(digit.getText().toString());
        }
        return code.toString();
    }

    private void clearCodeDigits() {
        for (EditText digit : codeDigits) {
            digit.setText("");
        }
        codeDigits[0].requestFocus();
        btnVerifyCode.setEnabled(false);
    }

    private void attemptVerifyCode() {
        String code = currentCode();

        if (code.length() != 6) {
            Toast.makeText(this, R.string.auth_login_empty_code_error, Toast.LENGTH_SHORT).show();
            return;
        }

        LoadingButtonHelper.setLoading(btnVerifyCode, true);
        authRepository.verifyCode(code, new OtpVerifyCallback() {
            @Override
            public void onSuccess() {
                LoadingButtonHelper.setLoading(btnVerifyCode, false);
                syncProfileAndContinue();
            }

            @Override
            public void onError(String message) {
                LoadingButtonHelper.setLoading(btnVerifyCode, false);
                Toast.makeText(LoginActivity.this, R.string.auth_login_verify_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCodeStep() {
        textCodeSentTo.setText(getString(R.string.auth_login_code_sent, phoneNumber));
        groupPhone.setVisibility(View.GONE);
        groupCode.setVisibility(View.VISIBLE);
        codeDigits[0].requestFocus();
    }

    private void showPhoneStep() {
        clearCodeDigits();
        groupCode.setVisibility(View.GONE);
        groupPhone.setVisibility(View.VISIBLE);
    }

    /**
     * POST /me es idempotente: crea el perfil si es la primera vez que este número inicia
     * sesión, o solo lo devuelve si ya existía. Si vuelve sin nombre, el perfil está incompleto
     * y hay que pedirlo antes de entrar a Home.
     */
    private void syncProfileAndContinue() {
        userRepository.syncProfile(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (profile.isComplete()) {
                    goToHome();
                } else {
                    goToCompleteProfile();
                }
            }

            @Override
            public void onError(ApiException error) {
                // Sin perfil no podemos saber si falta nombre; Home igual está protegido por
                // AuthenticatedActivity y puede reintentar la sincronización más tarde.
                goToHome();
            }
        });
    }

    private void goToHome() {
        if (driverRole) {
            DriverEntryPoint.route(this);
            return;
        }
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToCompleteProfile() {
        Intent intent = new Intent(this, CompleteProfileActivity.class);
        intent.putExtra(EXTRA_DRIVER_ROLE, driverRole);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
