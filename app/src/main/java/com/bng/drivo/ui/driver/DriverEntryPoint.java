package com.bng.drivo.ui.driver;

import android.app.Activity;
import android.content.Intent;

import com.bng.drivo.data.model.DriverApplication;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.RestDriverRepository;

/**
 * Punto de entrada único al modo conductor tras el login (desde LoginActivity o
 * CompleteProfileActivity). Consulta GET /driver/application antes de navegar para que un
 * conductor que se está registrando por primera vez vaya directo a
 * DriverRegistrationActivity — nunca pasa, ni de forma transitoria, por
 * DriverHomeActivity (que implica mapa y puede pedir permiso de ubicación, algo a lo que un
 * conductor sin aprobar no tiene acceso todavía).
 */
public final class DriverEntryPoint {

    private DriverEntryPoint() {
    }

    public static void route(Activity activity) {
        DriverRepository driverRepository = new RestDriverRepository(activity);
        driverRepository.getApplication(new ApiCallback<DriverApplication>() {
            @Override
            public void onSuccess(DriverApplication application) {
                goTo(activity, DriverHomeActivity.class);
            }

            @Override
            public void onError(ApiException error) {
                goTo(activity, error.getCode() == ApiErrorCode.NO_APPLICATION
                        ? DriverRegistrationActivity.class : DriverHomeActivity.class);
            }
        });
    }

    private static void goTo(Activity activity, Class<?> destination) {
        Intent intent = new Intent(activity, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
