package com.bng.drivo.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.bng.drivo.R;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RestDriverRepository;

/**
 * Botón "Desconectarme" del aviso "Estás en el radar".
 *
 * <p>Es un receptor y no una acción del propio {@link DriverOnlineService} a propósito: arrancar
 * un servicio desde segundo plano está restringido en Android 12+, y ese camino falla justo con
 * la app cerrada, que es cuando se usa este botón. Un broadcast desde una notificación siempre se
 * entrega, y parar un servicio no tiene restricción.
 *
 * <p>Primero se desconecta en el servidor y solo después se apaga el aviso: si no hay red, el
 * conductor sigue en el radar, y quitar el aviso le haría creer lo contrario.
 */
public class DriverGoOfflineReceiver extends BroadcastReceiver {

    /**
     * Aviso interno (solo este paquete) para que el inicio del conductor, si está abierto debajo
     * de la cortina de notificaciones, se entere sin esperar a su próximo onResume.
     */
    public static final String ACTION_WENT_OFFLINE = "com.bng.drivo.action.DRIVER_WENT_OFFLINE";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        PendingResult pending = goAsync();
        new RestDriverRepository(app).goOffline(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                DriverOnlineService.stop(app);
                app.sendBroadcast(new Intent(ACTION_WENT_OFFLINE).setPackage(app.getPackageName()));
                Toast.makeText(app, R.string.driver_service_went_offline_toast, Toast.LENGTH_SHORT).show();
                pending.finish();
            }

            @Override
            public void onError(ApiException error) {
                Toast.makeText(app, R.string.driver_home_disconnect_error, Toast.LENGTH_SHORT).show();
                pending.finish();
            }
        });
    }
}
