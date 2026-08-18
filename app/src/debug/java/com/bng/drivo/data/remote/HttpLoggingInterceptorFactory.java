package com.bng.drivo.data.remote;

import okhttp3.Interceptor;
import okhttp3.logging.HttpLoggingInterceptor;

/** Variante debug: logging-interceptor solo está en el classpath de este build type. */
final class HttpLoggingInterceptorFactory {

    private HttpLoggingInterceptorFactory() {
    }

    static Interceptor create() {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        return interceptor;
    }
}
