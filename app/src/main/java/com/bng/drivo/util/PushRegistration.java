package com.bng.drivo.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DeviceRepository;
import com.bng.drivo.data.repository.RestDeviceRepository;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Deja el aparato listo para recibir notificaciones: registra el token de FCM en
 * {@code POST /devices}.
 *
 * <p>Vivía dentro de HomeActivity, que es la pantalla del pasajero. Se sacó porque
 * <b>el conductor nunca pasa por ahí</b>: {@code DriverEntryPoint} lo manda directo a su registro
 * o a su inicio, así que un conductor que solo usa la app para trabajar no tenía token registrado
 * en el servidor — y sin token, {@code FcmPush} no le manda nada. Ni el viaje entrante, ni el
 * veredicto de su expediente.
 *
 * <p>El registro es idempotente: {@code POST /devices} hace upsert por token, así que llamarlo en
 * cada arranque no acumula filas y recupera al aparato que quedó fuera por un error de red.
 */
public final class PushRegistration {

    private PushRegistration() {
    }

    /**
     * Si en Android 13+ todavía falta el permiso para <b>mostrar</b> notificaciones.
     *
     * <p>Quien llama decide cuándo pedirlo: Android solo admite un diálogo de permiso a la vez, y
     * lanzar este junto al de ubicación descarta uno de los dos en silencio.
     */
    public static boolean needsNotificationPermission(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Manda el token al servidor.
     *
     * <p>Se llama con o sin el permiso concedido, a propósito: sin él FCM sigue entregando los
     * mensajes de datos y la app solo pierde la alerta visual. No registrar el token, en cambio,
     * deja al usuario sin nada.
     */
    public static void registerToken(Context context) {
        Context appContext = context.getApplicationContext();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            DeviceRepository deviceRepository = new RestDeviceRepository(appContext);
            deviceRepository.registerDevice(token, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // no-op
                }

                @Override
                public void onError(ApiException error) {
                    // Se reintenta en el siguiente arranque: esto corre en cada apertura.
                }
            });
        });
    }
}
