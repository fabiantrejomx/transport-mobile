package com.bng.drivo.data.repository;

import android.content.Context;

import com.bng.drivo.data.remote.ApiCallDispatcher;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiClient;
import com.bng.drivo.data.remote.TransportApiService;
import com.bng.drivo.data.remote.dto.DeviceRegisterRequest;

public class RestDeviceRepository implements DeviceRepository {

    private final TransportApiService service;

    public RestDeviceRepository(Context context) {
        this.service = ApiClient.getService(context);
    }

    @Override
    public void registerDevice(String fcmToken, ApiCallback<Void> callback) {
        ApiCallDispatcher.enqueue(service.registerDevice(new DeviceRegisterRequest(fcmToken)), callback);
    }
}
