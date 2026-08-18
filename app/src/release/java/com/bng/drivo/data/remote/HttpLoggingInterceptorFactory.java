package com.bng.drivo.data.remote;

import okhttp3.Interceptor;

/** Variante release: no hay logging-interceptor en el classpath (es debugImplementation). */
final class HttpLoggingInterceptorFactory {

    private HttpLoggingInterceptorFactory() {
    }

    static Interceptor create() {
        return null;
    }
}
