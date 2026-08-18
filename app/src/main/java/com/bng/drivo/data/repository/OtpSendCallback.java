package com.bng.drivo.data.repository;

public interface OtpSendCallback {

    void onCodeSent(String verificationId);

    /** Firebase verificó el número sin que el usuario tuviera que escribir el código. */
    void onAutoVerified();

    void onError(String message);
}
