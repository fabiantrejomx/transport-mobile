package com.bng.drivo.data.repository;

import android.app.Activity;

public interface AuthRepository {

    boolean isLoggedIn();

    /** Uid de Firebase Auth de la sesión actual, o null si no hay sesión. */
    String getCurrentUserId();

    void sendVerificationCode(Activity activity, String phoneNumber, OtpSendCallback callback);

    void verifyCode(String smsCode, OtpVerifyCallback callback);

    void logout();
}
