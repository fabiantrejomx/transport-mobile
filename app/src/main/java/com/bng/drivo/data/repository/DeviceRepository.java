package com.bng.drivo.data.repository;

import com.bng.drivo.data.remote.ApiCallback;

public interface DeviceRepository {

    /** POST /devices — llamar en cada arranque logueado y cada vez que FCM rote el token. */
    void registerDevice(String fcmToken, ApiCallback<Void> callback);
}
