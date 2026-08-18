package com.bng.drivo.data.repository;

import android.content.Context;

import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.remote.ApiCallDispatcher;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiClient;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.remote.TransportApiService;
import com.bng.drivo.data.remote.dto.FavoriteCreateRequest;
import com.bng.drivo.data.remote.dto.FavoriteDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RestAddressRepository implements AddressRepository {

    private final TransportApiService service;

    public RestAddressRepository(Context context) {
        this.service = ApiClient.getService(context);
    }

    @Override
    public void getAll(ApiCallback<List<SavedAddress>> callback) {
        ApiCallDispatcher.enqueue(service.getFavorites(), new ApiCallback<List<FavoriteDto>>() {
            @Override
            public void onSuccess(List<FavoriteDto> result) {
                List<SavedAddress> addresses = new ArrayList<>();
                for (FavoriteDto dto : result) {
                    addresses.add(toSavedAddress(dto));
                }
                callback.onSuccess(addresses);
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void create(String label, String addressText, double lat, double lng,
                        ApiCallback<SavedAddress> callback) {
        String idempotencyKey = UUID.randomUUID().toString();
        FavoriteCreateRequest body = new FavoriteCreateRequest(label, addressText, lat, lng);
        ApiCallDispatcher.enqueue(service.createFavorite(idempotencyKey, body), new ApiCallback<FavoriteDto>() {
            @Override
            public void onSuccess(FavoriteDto result) {
                callback.onSuccess(toSavedAddress(result));
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void delete(String id, ApiCallback<Void> callback) {
        ApiCallDispatcher.enqueue(service.deleteFavorite(id), callback);
    }

    private SavedAddress toSavedAddress(FavoriteDto dto) {
        return new SavedAddress(dto.id, dto.label, dto.address_text, dto.lat, dto.lng);
    }
}
