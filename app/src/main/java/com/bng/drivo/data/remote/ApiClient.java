package com.bng.drivo.data.remote;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Singleton del cliente Retrofit hacia transport-api. Base URL leída del manifest (mismo patrón que la API key de Maps en DrivoApplication). */
public final class ApiClient {

    private static final String BASE_URL_META_DATA = "com.bng.drivo.TRANSPORT_API_BASE_URL";
    private static final long TIMEOUT_SECONDS = 30L;

    private static volatile TransportApiService service;

    private ApiClient() {
    }

    public static TransportApiService getService(Context context) {
        if (service == null) {
            synchronized (ApiClient.class) {
                if (service == null) {
                    service = buildRetrofit(context).create(TransportApiService.class);
                }
            }
        }
        return service;
    }

    private static Retrofit buildRetrofit(Context context) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(new AuthTokenInterceptor());

        okhttp3.Interceptor loggingInterceptor = HttpLoggingInterceptorFactory.create();
        if (loggingInterceptor != null) {
            clientBuilder.addInterceptor(loggingInterceptor);
        }

        Gson gson = new GsonBuilder().create();

        return new Retrofit.Builder()
                .baseUrl(readBaseUrl(context.getApplicationContext()))
                .client(clientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    private static String readBaseUrl(Context context) {
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            String baseUrl = appInfo.metaData != null ? appInfo.metaData.getString(BASE_URL_META_DATA) : null;
            if (baseUrl == null || baseUrl.isEmpty() || baseUrl.equals("DEFAULT_API_BASE_URL")) {
                throw new IllegalStateException(
                        "TRANSPORT_API_BASE_URL no está configurada en local.properties");
            }
            return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("No se pudo leer el meta-data de TRANSPORT_API_BASE_URL", e);
        }
    }
}
