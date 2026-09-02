package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.NearbyUnit;
import com.bng.drivo.data.model.Offer;
import com.bng.drivo.data.model.Quote;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.model.Waypoint;
import com.bng.drivo.data.remote.ApiCallback;

import java.util.List;

public interface TripRepository {

    /**
     * GET /nearby-drivers — hasta tres unidades disponibles alrededor del punto, para que el mapa
     * de inicio no se vea muerto antes de que el pasajero escriba su destino.
     *
     * <p>Es una foto, no un radar: los puntos vienen redondeados a una celda de 150 m, sin
     * identidad de ningún tipo, y pueden traer hasta minuto y medio de antigüedad. Una lista vacía
     * es una respuesta normal —no hay nadie en el radio, o hay menos unidades que el mínimo que el
     * servidor considera útil mostrar—, nunca un error.
     */
    void getNearbyDrivers(double lat, double lng, ApiCallback<List<NearbyUnit>> callback);

    /** POST /quotes — {@code waypoints} puede venir null/vacío (sin parada). */
    void createQuote(double originLat, double originLng, double destLat, double destLng,
                      String originText, String destText, List<Waypoint> waypoints, ApiCallback<Quote> callback);

    /** POST /rides — "pedir el viaje": la oferta debe caer entre floor y ceiling de la cotización. */
    void createRide(String quoteId, double offer, ApiCallback<Ride> callback);

    /**
     * GET /rides/{id}/offers — la cola de ofertas vivas de la subasta.
     *
     * <p>Es la misma lista que el canal en vivo publica en {@code rides/{id}/offers}, servida por
     * HTTP. Existe porque Firestore aquí es una proyección de Postgres, no la verdad: si ese canal
     * no llega —reglas, red, proyección con retraso— la subasta tiene que seguir funcionando, y
     * este endpoint la sostiene sin depender de nada de Firebase.
     */
    void getOffers(String rideId, ApiCallback<List<Offer>> callback);

    /** POST /rides/{id}/accept-offer */
    void acceptOffer(String rideId, String offerId, ApiCallback<Ride> callback);

    /** POST /rides/{id}/reject-offer */
    void rejectOffer(String rideId, String offerId, ApiCallback<Void> callback);

    /** POST /rides/{id}/cancel — solo antes de IN_PROGRESS; después, cierra el viaje el conductor. */
    void cancelRide(String rideId, ApiCallback<Ride> callback);

    /** POST /sos — devuelve la URL pública de rastreo. */
    void sendSos(String rideId, double lat, double lng, ApiCallback<String> callback);

    /** POST /rides/{id}/rating */
    void rateRide(String rideId, int stars, String comment, ApiCallback<Void> callback);

    /** GET /rides?role=passenger&limit={limit} — nunca trae al conductor, ver RideSummary. */
    void getRideHistory(int limit, ApiCallback<List<RideSummary>> callback);

    /** GET /rides/{id} — el único que sí trae al conductor. */
    void getRideDetail(String rideId, ApiCallback<Ride> callback);
}
