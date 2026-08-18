package com.bng.drivo.data.remote;

import androidx.annotation.NonNull;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Adapta un {@link Call} de Retrofit a {@link ApiCallback}. Todos los repositorios pasan por
 * aquí para que el parseo de error {@code {code, message}} viva en un único lugar (ver
 * {@link ApiException#from}), nunca repetido pantalla por pantalla.
 */
public final class ApiCallDispatcher {

    private ApiCallDispatcher() {
    }

    public static <T> void enqueue(Call<T> call, ApiCallback<T> callback) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(ApiException.from(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                callback.onError(ApiException.networkError(t));
            }
        });
    }
}
