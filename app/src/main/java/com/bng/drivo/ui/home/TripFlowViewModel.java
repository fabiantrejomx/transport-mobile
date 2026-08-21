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
    @Nullable
    private Waypoint stop;
    @Nullable
    private Quote quote;
    @Nullable
    private String rideId;
    private float offeredFare;

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
    public void startDestination(LatLng origin, String originText, LatLng destination, String destinationText) {
        this.origin = origin;
        this.originText = originText;
        this.destination = destination;
        this.destinationText = destinationText;
        this.stop = null;
        this.quote = null;
        this.rideId = null;
        this.offeredFare = 0f;
    }

    /** Vuelta a Home: se descarta la solicitud en curso, el origen se recalcula al empezar otra. */
    public void clearTrip() {
        destination = null;
        destinationText = null;
        stop = null;
        quote = null;
        rideId = null;
        offeredFare = 0f;
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

    @Override
    protected void onCleared() {
        super.onCleared();
        stepListener = null;
    }
}
