package com.bng.drivo.data.remote;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/** Único punto donde se parsea un error de transport-api — los repositorios nunca leen el body a mano. */
public class ApiException extends Exception {

    private static final Gson GSON = new Gson();

    private final ApiErrorCode code;
    private final int httpStatus;

    private ApiException(ApiErrorCode code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    static ApiException from(Response<?> response) {
        ResponseBody errorBody = response.errorBody();
        if (errorBody != null) {
            try {
                ApiErrorBody body = GSON.fromJson(errorBody.string(), ApiErrorBody.class);
                if (body != null && body.code != null) {
                    ApiErrorCode code = parseCode(body.code);
                    return new ApiException(code, body.message, response.code());
                }
            } catch (JsonSyntaxException | java.io.IOException ignored) {
                // cae al UNKNOWN genérico de abajo
            }
        }
        return new ApiException(ApiErrorCode.UNKNOWN, "Error inesperado del servidor (" + response.code() + ")",
                response.code());
    }

    static ApiException networkError(Throwable t) {
        return new ApiException(ApiErrorCode.NETWORK_ERROR,
                t.getMessage() != null ? t.getMessage() : "Sin conexión con el servidor", 0);
    }

    private static ApiErrorCode parseCode(String rawCode) {
        try {
            return ApiErrorCode.valueOf(rawCode);
        } catch (IllegalArgumentException e) {
            return ApiErrorCode.UNKNOWN;
        }
    }
}
