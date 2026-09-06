package com.bng.drivo.ui.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.service.DriverOnlineService;
import com.bng.drivo.ui.home.HomeActivity;
import com.bng.drivo.util.PrefsHelper;
import com.bng.drivo.util.SubmittedApplicationCache;

/**
 * Las dos formas de salir de una pantalla de la que no se puede avanzar.
 *
 * <p>El registro de conductor y la tarjeta de "expediente no aprobado" eran callejones sin salida:
 * se llegaba a ellas con el task limpio ({@code CLEAR_TASK}), no tenían cajón ni barra, y el botón
 * de cerrar sesión vivía en una pantalla que desde ahí no se podía abrir. Un conductor que se
 * equivocó de rol, que puso otro número, o que simplemente no quiere terminar el registro hoy, no
 * tenía más salida que desinstalar — durante los días que tarda el veredicto del backoffice.
 *
 * <p>Son dos salidas y no una porque resuelven cosas distintas:
 *
 * <ul>
 *   <li>{@link #toPassenger} — <b>misma cuenta</b>, otro modo. Ser conductor es un atributo del
 *       usuario, no una cuenta aparte: el contrato tiene un solo {@code /me} y el rol es una
 *       preferencia de este teléfono. Nada del expediente se pierde ni se cancela.</li>
 *   <li>{@link #logout} — <b>otra cuenta</b>. Para quien tiene su cuenta de pasajero en otro
 *       número o correo, que es un caso real y no una variante teórica del anterior.</li>
 * </ul>
 */
public final class SessionExit {

    private SessionExit() {}

    /**
     * Cambia a modo pasajero sin tocar la sesión.
     *
     * <p>El expediente se queda como está en el servidor y se puede retomar desde Configuración →
     * "Conducir con Drivo". Lo que <b>no</b> puede quedarse como estaba es la disponibilidad:
     * cambiar de modo tiene que sacar al conductor del radar.
     *
     * <p>Si no, el servidor lo sigue teniendo por disponible —el servicio en primer plano seguiría
     * reportando su posición, así que el barredor tampoco lo daría por perdido— y le seguiría
     * ofreciendo viajes a una app que ya no tiene ni bandeja ni pantalla donde enseñarlos. Cada
     * uno de esos viajes es un pasajero esperando a alguien que no va a llegar.
     */
    public static void toPassenger(@NonNull Activity activity) {
        leaveTheRadar(activity);
        new PrefsHelper(activity).putBoolean(RoleSelectionActivity.PREF_KEY_DRIVER_MODE, false);
        goTo(activity, HomeActivity.class);
    }

    /** El camino de vuelta: modo conductor con la misma cuenta. Quién enruta después es
     * {@link com.bng.drivo.ui.driver.DriverEntryPoint}, que ya sabe si toca registro o inicio. */
    public static void toDriver(@NonNull Activity activity) {
        new PrefsHelper(activity).putBoolean(RoleSelectionActivity.PREF_KEY_DRIVER_MODE, true);
        com.bng.drivo.ui.driver.DriverEntryPoint.route(activity);
    }

    /**
     * Cierra la sesión y vuelve a la elección de rol.
     *
     * <p>Limpia también lo que este teléfono guardó de la cuenta anterior: el modo, y la copia
     * local del expediente. Sin eso, la siguiente cuenta que entrara en este mismo teléfono vería
     * el CURP y el vehículo del conductor anterior en su pantalla de datos —
     * {@code SubmittedApplicationCache} no está atado a ningún uid.
     */
    public static void logout(@NonNull Activity activity) {
        // Antes de cerrar la sesión, no después: el token de Firebase todavía sirve para que el
        // servidor acepte el POST. Al revés, la llamada saldría sin autenticar y el conductor se
        // quedaría en el radar sin app que lo atendiera.
        leaveTheRadar(activity);
        new FirebaseAuthRepository().logout();
        clearLocalSession(activity);
        goTo(activity, RoleSelectionActivity.class);
    }

    /**
     * Saca al conductor del radar y apaga el aviso permanente.
     *
     * <p>No se espera la respuesta: la pantalla cambia igual. Si el POST se pierde por falta de
     * red, el conductor deja de reportar posición al parar el servicio, y el barredor del servidor
     * lo desconecta solo a los cinco minutos ({@code STALE_MINUTES}). La llamada es el camino
     * rápido; parar el servicio es la garantía.
     */
    private static void leaveTheRadar(@NonNull Activity activity) {
        new RestDriverRepository(activity).goOffline(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // no-op
            }

            @Override
            public void onError(ApiException error) {
                // Ver el javadoc: el barredor lo cubre.
            }
        });
        DriverOnlineService.stop(activity);
    }

    /** Lo que queda de la cuenta anterior en este teléfono y no debe sobrevivirla. */
    private static void clearLocalSession(Context context) {
        SubmittedApplicationCache.clear(context);
        new PrefsHelper(context).remove(RoleSelectionActivity.PREF_KEY_DRIVER_MODE);
    }

    private static void goTo(Activity activity, Class<?> destination) {
        Intent intent = new Intent(activity, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
