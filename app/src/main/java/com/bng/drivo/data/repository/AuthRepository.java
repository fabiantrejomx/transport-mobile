package com.bng.drivo.data.repository;

import android.app.Activity;

public interface AuthRepository {

    boolean isLoggedIn();

    void sendVerificationCode(Activity activity, String phoneNumber, OtpSendCallback callback);

    void verifyCode(String smsCode, OtpVerifyCallback callback);

    void logout();
}
