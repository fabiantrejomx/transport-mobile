package com.bng.drivo.data.remote.dto;

public class DeviceRegisterRequest {
    public String fcm_token;
    public String platform;

    public DeviceRegisterRequest(String fcmToken) {
        this.fcm_token = fcmToken;
        this.platform = "android";
    }
}
