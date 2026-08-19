package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.Quote;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.model.Waypoint;
import com.bng.drivo.data.remote.ApiCallback;

import java.util.List;

public interface TripRepository {

    /** POST /quotes — {@code waypoints} puede venir null/vacío (sin parada). */
    void createQuote(double originLat, double originLng, double destLat, double destLng,
                      String originText, String destText, List<Waypoint> waypoints, ApiCallback<Quote> callback);

    /** POST /rides — "pedir el viaje": la oferta debe caer entre floor y ceiling de la cotización. */
    void createRide(String quoteId, double offer, ApiCallback<Ride> callback);

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
