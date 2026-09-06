package com.bng.drivo.ui.trip;

import androidx.annotation.NonNull;

import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.TripRepository;

import java.util.List;

/**
 * Lo primero que hace la app del pasajero al arrancar: preguntarle al servidor qué tiene abierto.
 *
 * <p>El viaje vive en el servidor y esta app es una ventana. Antes no era así: el destino, la
 * subasta y el viaje en curso vivían en la memoria del proceso (un ViewModel y los extras de un
 * Intent), y cerrar la app se los llevaba. El caso feo no era perder el destino a medio escribir
 * —eso se vuelve a escribir— sino quedarse sin la pantalla del viaje con un coche real en camino,
 * y encima sin poder pedir otro, porque el servidor lo rechazaba con {@code RIDE_IN_PROGRESS}.
 *
 * <p>Qué se limpia y qué se retoma, que es la decisión de fondo:
 *
 * <ul>
 *   <li><b>Antes de pedir</b> (destino elegido, parada, tarifa en el slider): se limpia. No existe
 *       en el servidor, se rehace en dos toques, y retomarlo a medias es ambiguo.</li>
 *   <li><b>Búsqueda viva</b>: se retoma. El viaje ya existe y su reloj sigue corriendo con o sin
 *       app abierta.</li>
 *   <li><b>Conductor asignado, esperando o en curso</b>: se retoma siempre. Es la razón de ser de
 *       todo esto.</li>
 *   <li><b>Terminado sin calificar</b>: se ofrece calificar el último viaje. No se "retoma" un
 *       viaje cerrado; lo que queda pendiente es la calificación.</li>
 *   <li><b>Cancelado o expirado</b>: se limpia. Ya no hay nada que hacer.</li>
 * </ul>
 *
 * <p>Sin red no bloquea nada: si la consulta falla se sigue al inicio normal. Un arranque detenido
 * por un servidor que no contesta es peor que un inicio que no sabe del viaje — y la siguiente
 * apertura vuelve a preguntar.
 */
public final class TripResumeGate {

    /** Estados en los que el viaje sigue vivo. Los mismos que el servidor deja retomar. */
    private static final String STATUS_SEARCHING = "SEARCHING";

    public interface Callbacks {
        /**
         * Subasta todavía abierta: vuelve al radar con este viaje.
         *
         * <p>El {@code Ride} trae origen, destino, paradas, trazo y lo ofrecido — todo lo que el
         * paso necesita para repintarse sin la cotización, que se perdió con el proceso.
         */
        void onResumeSearching(@NonNull Ride ride);

        /** MATCHED / DRIVER_ARRIVED / IN_PROGRESS: abre la pantalla del viaje. */
        void onResumeActiveTrip(@NonNull Ride ride);

        /** El último viaje terminó y no se calificó. */
        void onPendingRating(@NonNull Ride ride);

        /** No hay nada que retomar (o no se pudo saber): seguir al inicio de siempre. */
        void onNothingToResume();
    }

    private TripResumeGate() {}

    public static void run(@NonNull TripRepository trips, @NonNull Callbacks callbacks) {
        trips.getCurrentRide(new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (ride == null || ride.getStatus() == null) {
                    // 204: nada abierto. Todavía puede quedar una calificación del último viaje.
                    checkPendingRating(trips, callbacks);
                    return;
                }
                if (STATUS_SEARCHING.equals(ride.getStatus())) {
                    callbacks.onResumeSearching(ride);
                } else {
                    callbacks.onResumeActiveTrip(ride);
                }
            }

            @Override
            public void onError(ApiException error) {
                callbacks.onNothingToResume();
            }
        });
    }

    /**
     * Solo el último viaje, no todos los sin calificar.
     *
     * <p>Es la decisión de producto: perseguir al pasajero con calificaciones viejas cada vez que
     * abre la app convierte una cortesía en un peaje. El que acaba de terminar es el que todavía
     * recuerda.
     */
    private static void checkPendingRating(TripRepository trips, Callbacks callbacks) {
        trips.getRideHistory(1, new ApiCallback<List<RideSummary>>() {
            @Override
            public void onSuccess(List<RideSummary> history) {
                if (history == null || history.isEmpty() || !history.get(0).needsRating()) {
                    callbacks.onNothingToResume();
                    return;
                }
                // El resumen no trae al conductor y la pantalla de recibo lo enseña, así que
                // hace falta el detalle. Es una llamada de más en un caso poco frecuente.
                trips.getRideDetail(history.get(0).getId(), new ApiCallback<Ride>() {
                    @Override
                    public void onSuccess(Ride ride) {
                        if (ride == null) {
                            callbacks.onNothingToResume();
                            return;
                        }
                        callbacks.onPendingRating(ride);
                    }

                    @Override
                    public void onError(ApiException error) {
                        callbacks.onNothingToResume();
                    }
                });
            }

            @Override
            public void onError(ApiException error) {
                callbacks.onNothingToResume();
            }
        });
    }
}
