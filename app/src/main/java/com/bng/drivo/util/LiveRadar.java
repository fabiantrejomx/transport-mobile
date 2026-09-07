package com.bng.drivo.util;

import androidx.annotation.Nullable;

/**
 * El puente entre un push de viaje y el radar del conductor que ya está a la vista.
 *
 * <p>Existe por un fallo real: silenciar la notificación cuando el inicio del conductor está
 * delante daba por hecho que la bandeja de Firestore iba a entregar el viaje. El 2026-09-07 el
 * canal en vivo tardó más de diez segundos en levantar ({@code Could not reach Cloud Firestore
 * backend}) y, como la notificación tampoco se pintó, las solicitudes no aparecieron por ningún
 * lado: ni modal, ni aviso, ni rastro. El conductor estaba "en línea" mirando un mapa vacío
 * mientras el servidor le ofrecía viajes.
 *
 * <p>La regla que sale de ahí, y que esta clase hace cumplir: <b>no se silencia un aviso que nadie
 * recibió</b>. En vez de preguntar "¿hay una pantalla delante?" —que solo dice quién <i>podría</i>
 * atenderlo— el servicio de push le entrega el viaje al radar y mira la respuesta. Si lo tomó, no
 * hay nada que notificar; si no hay radar atado, la notificación se pinta como siempre.
 *
 * <p>De paso, el push deja de ser solo un atajo para tocar: con la bandeja caída, el modal se abre
 * igual y en el momento, porque el aviso ya no termina en la barra de notificaciones sino en el
 * mismo {@code fetchIncomingRequest} que dispara el canal en vivo. Son dos caminos independientes
 * hacia la misma pantalla, que es lo que hace que el tiempo real sobreviva a que uno de los dos
 * falle — FCM tiene su propia cola y reintentos, y aguanta cortes que a Firestore lo tumban.
 *
 * <p>Es estático porque quien pregunta es un {@code Service}, que no alcanza a ninguna Activity, y
 * volátil porque se escribe en el hilo principal y se lee en el de FCM. Se ata en {@code onResume}
 * y se suelta en {@code onPause}: fuera de ese rango la notificación es justamente lo que hace
 * falta. Ver también {@link VisibleScreen}, que sigue resolviendo los avisos que no tienen a quién
 * entregarse.
 */
public final class LiveRadar {

    /**
     * Lo implementa el inicio del conductor mientras está en primer plano.
     *
     * <p>Cada método devuelve si el radar se hizo cargo del aviso. Devolver {@code false} no es un
     * error: significa "yo no puedo con esto ahora", y la consecuencia correcta es que se pinte la
     * notificación.
     */
    public interface Handler {

        /** Una solicitud entrante: abrirla, o guardarla si el modal está ocupado. */
        boolean onNewRide(String rideId);

        /** Un viaje que ya no está disponible: quitarlo de pantalla si es el que se está leyendo. */
        boolean onRideTaken(@Nullable String rideId);

        /** Nos eligieron: preguntar al servidor y abrir el viaje asignado. */
        boolean onOfferAccepted();
    }

    @Nullable
    private static volatile Handler handler;

    private LiveRadar() {}

    public static void attach(Handler radar) {
        handler = radar;
    }

    /**
     * Comprueba que siga siendo el mismo radar antes de soltarlo, para que el {@code onPause} de
     * una instancia que se va no borre el {@code onResume} de la que llega — Android los ejecuta
     * en ese orden.
     */
    public static void detach(Handler radar) {
        if (handler == radar) {
            handler = null;
        }
    }

    public static boolean deliverNewRide(@Nullable String rideId) {
        Handler radar = handler;
        return rideId != null && radar != null && radar.onNewRide(rideId);
    }

    public static boolean deliverRideTaken(@Nullable String rideId) {
        Handler radar = handler;
        return radar != null && radar.onRideTaken(rideId);
    }

    public static boolean deliverOfferAccepted() {
        Handler radar = handler;
        return radar != null && radar.onOfferAccepted();
    }
}
