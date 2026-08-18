package com.bng.drivo.data.remote;

public interface ApiCallback<T> {
    void onSuccess(T result);

    void onError(ApiException error);
}
