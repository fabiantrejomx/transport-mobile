package com.bng.drivo.util;

import androidx.annotation.Nullable;

/**
 * Qué pantalla de Drivo tiene el usuario delante ahora mismo, si alguna.
 *
 * <p>Existe para una sola pregunta, y la hace {@code DrivoFirebaseMessagingService}: ¿hace falta
 * pintar esta notificación, o ya hay una pantalla a la vista que va a atender el aviso en vivo?
 * Con el radar del conductor delante, una solicitud entrante llega por la bandeja de Firestore y
 * se abre en el modal con su tono y su vibración (ver {@link RideAlert}); una notificación encima
 * de eso no cuenta nada nuevo y sí añade una forma de salirse de la pantalla en la que hay que
 * decidir.
 *
 * <p>Es estático porque quien pregunta es un Service, que no tiene manera de alcanzar una
 * Activity, y volátil porque se escribe en el hilo principal y se lee en el de FCM.
 *
 * <p>Se apunta en {@code onResume} y se borra en {@code onPause} —no en {@code onStart}/
 * {@code onStop}— porque lo que importa es si el usuario lo está <b>viendo</b>: con un diálogo del
 * sistema o la pantalla apagada encima ya no lo ve, y ahí la notificación vuelve a hacer falta.
 * El borrado comprueba antes que siga siendo la misma pantalla, para que el {@code onPause} de la
 * que se va no borre el {@code onResume} de la que llega (Android los ejecuta en ese orden).
 */
public final class VisibleScreen {

    @Nullable
    private static volatile Class<?> current;

    private VisibleScreen() {}

    public static void show(Class<?> screen) {
        current = screen;
    }

    public static void hide(Class<?> screen) {
        if (current == screen) {
            current = null;
        }
    }

    public static boolean isShowing(Class<?> screen) {
        return current == screen;
    }
}
