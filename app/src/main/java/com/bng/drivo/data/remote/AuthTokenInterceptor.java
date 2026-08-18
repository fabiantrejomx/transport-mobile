package com.bng.drivo.data.remote;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Adjunta {@code Authorization: Bearer <idToken de Firebase>} a cada petición. Si el servidor
 * responde 401 ({@code UNAUTHENTICATED}), reintenta una sola vez forzando refresh del token
 * (puede haber vencido entre que se pidió y se usó).
 *
 * Corre en el hilo de OkHttp (nunca el principal), así que bloquear con {@link Tasks#await} es
 * seguro y es el patrón estándar para esto.
 */
class AuthTokenInterceptor implements Interceptor {

    private static final long TOKEN_WAIT_SECONDS = 10L;

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String token = fetchIdToken(false);

        Response response = chain.proceed(withAuthHeader(original, token));
        if (response.code() != 401 || token == null) {
            return response;
        }

        response.close();
        String freshToken = fetchIdToken(true);
        return chain.proceed(withAuthHeader(original, freshToken));
    }

    private Request withAuthHeader(Request original, String token) {
        if (token == null) {
            return original;
        }
        return original.newBuilder().header("Authorization", "Bearer " + token).build();
    }

    private String fetchIdToken(boolean forceRefresh) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return null;
        }
        try {
            return Tasks.await(user.getIdToken(forceRefresh), TOKEN_WAIT_SECONDS, TimeUnit.SECONDS)
                    .getToken();
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            return null;
        }
    }
}
