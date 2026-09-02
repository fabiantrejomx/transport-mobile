package com.bng.drivo.data.repository;

import android.content.Context;

import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallDispatcher;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiClient;
import com.bng.drivo.data.remote.TransportApiService;
import com.bng.drivo.data.remote.dto.MeDto;
import com.bng.drivo.data.remote.dto.UpdateMeRequest;

public class RestUserRepository implements UserRepository {

    private final TransportApiService service;

    public RestUserRepository(Context context) {
        this.service = ApiClient.getService(context);
    }

    @Override
    public void getCurrentUser(ApiCallback<UserProfile> callback) {
        ApiCallDispatcher.enqueue(service.getMe(), mapping(callback));
    }

    @Override
    public void syncProfile(ApiCallback<UserProfile> callback) {
        ApiCallDispatcher.enqueue(service.createMe(), mapping(callback));
    }

    @Override
    public void updateProfile(String name, String email, ApiCallback<UserProfile> callback) {
        ApiCallDispatcher.enqueue(service.updateMe(new UpdateMeRequest(name, email, null)), mapping(callback));
    }

    private ApiCallback<MeDto> mapping(ApiCallback<UserProfile> callback) {
        return new ApiCallback<MeDto>() {
            @Override
            public void onSuccess(MeDto result) {
                callback.onSuccess(toUserProfile(result));
            }

            @Override
            public void onError(com.bng.drivo.data.remote.ApiException error) {
                callback.onError(error);
            }
        };
    }

    private UserProfile toUserProfile(MeDto dto) {
        return new UserProfile(dto.id, dto.name, dto.email, dto.phone, dto.photo_url,
                dto.rating, dto.trips);
    }
}
