package com.bng.drivo.data.repository;

import android.app.Activity;

public interface AuthRepository {

    boolean isLoggedIn();

    /** Uid de Firebase Auth de la sesión actual, o null si no hay sesión. */
    String getCurrentUserId();

    void sendVerificationCode(Activity activity, String phoneNumber, OtpSendCallback callback);

    void verifyCode(String smsCode, OtpVerifyCallback callback);

    /**
     * Alta o entrada con Google, alternativa al OTP. No pide código: quien firma la identidad es
     * Google, no un SMS. Termina igual que {@link #verifyCode} — con sesión de Firebase creada —
     * así que lo que sigue después es el mismo camino para las dos vías.
     */
    void signInWithGoogle(Activity activity, GoogleSignInCallback callback);

    void logout();
}
