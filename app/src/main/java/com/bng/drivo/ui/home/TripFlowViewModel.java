package com.bng.drivo.ui.home;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.bng.drivo.data.model.Quote;
import com.bng.drivo.data.model.Waypoint;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estado del flujo de solicitud del pasajero: destino elegido → tarifa → radar de conductores.
 *
 * <p>Antes esto vivía repartido en extras de Intent entre tres Activities (HomeFragment →
 * ConfirmPriceActivity → SearchingDriverActivity), copiados a mano en cada salto. Ahora los tres
 * pasos ocurren sobre el mismo mapa dentro de {@link HomeFragment} (ver su javadoc), así que el
 * estado tiene que sobrevivir a los cambios de configuración por su cuenta — que es justo lo que
 * un ViewModel de Activity hace, y de paso lo comparte con los paneles de cada paso.
 *
 * <p>Se usa un listener propio en vez de LiveData a propósito: el host es uno solo y repinta el
 * paso actual en cuanto se enlaza, así que no hace falta la maquinaria de observadores.
 */
public class TripFlowViewModel extends ViewModel {

    /** Los pasos que comparten mapa. El viaje aceptado sigue siendo una Activity aparte. */
    public enum Step {
        /** Home: saludo, buscador y listas. Sin ruta en el mapa. */
        IDLE,
        /** Pin fijo al centro, el mapa se arrastra debajo para elegir el destino. */
        PICK_LOCATION,
        /** Destino elegido: ruta dibujada, tarifa negociable en el modal. */
        CONFIRM_PRICE,
        /** Pin fijo al centro sobre la ruta ya dibujada, para elegir una parada intermedia. */
        PICK_STOP,
        /** Viaje ya creado: radar sobre el mapa y ofertas de una en una. */
        SEARCHING
    }

    public interface StepListener {
        void onStepChanged(Step step);
    }

    private Step step = Step.IDLE;
    @Nullable
    private StepListener stepListener;

    @Nullable
    private LatLng origin;
    @Nullable
    private String originText;
    @Nullable
    private LatLng destination;
    @Nullable
    private String destinationText;
    /**
     * Nombre que el usuario le puso a la dirección guardada de la que salió este destino ("Casa",
     * "Trabajo"). Null si lo eligió de cualquier otra forma. <b>Es solo para enseñar</b>: a la API
     * viaja {@link #destinationText}, que es la dirección de verdad.
     */
    @Nullable
    private String destinationLabel;
    @Nullable
    private Waypoint stop;
    @Nullable
    private Quote quote;
    @Nullable
    private String rideId;
    private float offeredFare;
    /**
     * El trazo por calles de un viaje <b>retomado</b>: el que venía en GET /current-ride cuando la
     * app se reabrió con una subasta ya viva.
     *
     * <p>Existe porque en ese arranque no hay cotización de la que sacarlo — la cotización se
     * quedó en el proceso que se cerró—, y sin él la subasta retomada dibujaría la guía recta
     * sobre una ruta que el servidor ya había calculado. Ver {@link #getRoutePolyline()}.
     */
    @Nullable
    private String resumedPolyline;
    /**
     * Cuándo vence la búsqueda, según el servidor (ISO-8601).
     *
     * <p>Es el único reloj de la subasta —las ofertas mueren con ella— y viene del servidor en los
     * dos caminos: al crear el viaje (POST /rides) y al retomarlo (GET /current-ride). Que sea el
     * mismo dato en ambos es lo que hace que reabrir la app a media espera continúe la cuenta en
     * vez de reiniciarla.
     */
    @Nullable
    private String searchExpiresAt;

    public Step getStep() {
        return step;
    }

    /**
     * Enlazar no dispara el callback: el host pinta el paso actual él mismo al crear su vista
     * (así cubre igual el primer arranque y la vuelta de un cambio de configuración).
     */
    public void setStepListener(@Nullable StepListener listener) {
        this.stepListener = listener;
    }

    public void goTo(Step next) {
        if (step == next) {
            return;
        }
        step = next;
        if (stepListener != null) {
            stepListener.onStepChanged(next);
        }
    }

    /** Destino recién elegido (buscador, dirección guardada o pin en el mapa): reinicia el resto. */
    public void startDestination(LatLng origin, String originText, LatLng destination,
                                 String destinationText, @Nullable String destinationLabel) {
        this.origin = origin;
        this.originText = originText;
        this.destination = destination;
        this.destinationText = destinationText;
        this.destinationLabel = destinationLabel;
        this.stop = null;
        this.quote = null;
        this.rideId = null;
        this.offeredFare = 0f;
        this.resumedPolyline = null;
        this.searchExpiresAt = null;
    }

    /** Vuelta a Home: se descarta la solicitud en curso, el origen se recalcula al empezar otra. */
    public void clearTrip() {
        destination = null;
        destinationText = null;
        destinationLabel = null;
        stop = null;
        quote = null;
        rideId = null;
        offeredFare = 0f;
        resumedPolyline = null;
        searchExpiresAt = null;
    }

    /** Origen, parada (si la hay) y destino, en orden — lo que el mapa dibuja como guía. */
    public List<LatLng> getRoutePoints() {
        if (origin == null || destination == null) {
            return Collections.emptyList();
        }
        List<LatLng> points = new ArrayList<>(3);
        points.add(origin);
        if (stop != null) {
            points.add(new LatLng(stop.getLat(), stop.getLng()));
        }
        points.add(destination);
        return points;
    }

    /** Formato que espera POST /quotes: null cuando no hay parada, nunca lista vacía. */
    @Nullable
    public List<Waypoint> getWaypoints() {
        return stop != null ? Collections.singletonList(stop) : null;
    }

    @Nullable
    public LatLng getOrigin() {
        return origin;
    }

    @Nullable
    public String getOriginText() {
        return originText;
    }

    @Nullable
    public LatLng getDestination() {
        return destination;
    }

    @Nullable
    public String getDestinationText() {
        return destinationText;
    }

    /** Ver {@link #destinationLabel}: null salvo que el destino sea una dirección guardada. */
    @Nullable
    public String getDestinationLabel() {
        return destinationLabel;
    }

    @Nullable
    public Waypoint getStop() {
        return stop;
    }

    public void setStop(@Nullable Waypoint stop) {
        this.stop = stop;
    }

    @Nullable
    public Quote getQuote() {
        return quote;
    }

    public void setQuote(@Nullable Quote quote) {
        this.quote = quote;
    }

    @Nullable
    public String getRideId() {
        return rideId;
    }

    public void setRideId(@Nullable String rideId) {
        this.rideId = rideId;
    }

    public float getOfferedFare() {
        return offeredFare;
    }

    public void setOfferedFare(float offeredFare) {
        this.offeredFare = offeredFare;
    }

    /**
     * El trazo por calles vigente, venga de donde venga: de la cotización en el flujo normal, o
     * del viaje ya creado cuando la app se reabre a media subasta. Null si no hay ninguno, y
     * entonces el mapa cae a la guía recta.
     *
     * <p>Un solo sitio que responda "¿qué trazo se dibuja ahora?" evita que cada quien pregunte
     * por la cotización y se quede sin ruta justo en el caso que no la tiene.
     */
    @Nullable
    public String getRoutePolyline() {
        return quote != null && quote.getPolyline() != null ? quote.getPolyline() : resumedPolyline;
    }

    public void setResumedPolyline(@Nullable String resumedPolyline) {
        this.resumedPolyline = resumedPolyline;
    }

    @Nullable
    public String getSearchExpiresAt() {
        return searchExpiresAt;
    }

    public void setSearchExpiresAt(@Nullable String searchExpiresAt) {
        this.searchExpiresAt = searchExpiresAt;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stepListener = null;
    }
}
