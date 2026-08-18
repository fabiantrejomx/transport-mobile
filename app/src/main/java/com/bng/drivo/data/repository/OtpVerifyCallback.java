package com.bng.drivo.data.repository;

public interface OtpVerifyCallback {

    void onSuccess();

    void onError(String message);
}
