package com.bng.drivo.data.repository;

import android.content.Context;

import com.bng.drivo.data.model.NearbyUnit;
import com.bng.drivo.data.model.Offer;
import com.bng.drivo.data.model.Quote;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.model.Waypoint;
import com.bng.drivo.data.remote.ApiCallDispatcher;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiClient;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.remote.TransportApiService;
import com.bng.drivo.data.remote.dto.CreateRideRequest;
import com.bng.drivo.data.remote.dto.DriverSummaryDto;
import com.bng.drivo.data.remote.dto.LatLngDto;
import com.bng.drivo.data.remote.dto.NearbyDriversDto;
import com.bng.drivo.data.remote.dto.NearbyUnitDto;
import com.bng.drivo.data.remote.dto.OfferCardDto;
import com.bng.drivo.data.remote.dto.OfferIdRequest;
import com.bng.drivo.data.remote.dto.PlaceDto;
import com.bng.drivo.data.remote.dto.QuoteDto;
import com.bng.drivo.data.remote.dto.QuoteRequest;
import com.bng.drivo.data.remote.dto.RatingRequest;
import com.bng.drivo.data.remote.dto.RideDto;
import com.bng.drivo.data.remote.dto.RideSummaryDto;
import com.bng.drivo.data.remote.dto.SosRequest;
import com.bng.drivo.data.remote.dto.SosResponseDto;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RestTripRepository implements TripRepository {

    private final TransportApiService service;

    public RestTripRepository(Context context) {
        this.service = ApiClient.getService(context);
    }

    @Override
    public void getNearbyDrivers(double lat, double lng, ApiCallback<List<NearbyUnit>> callback) {
        ApiCallDispatcher.enqueue(service.getNearbyDrivers(lat, lng), new ApiCallback<NearbyDriversDto>() {
            @Override
            public void onSuccess(NearbyDriversDto result) {
                List<NearbyUnit> units = new ArrayList<>();
                if (result != null && result.drivers != null) {
                    for (NearbyUnitDto dto : result.drivers) {
                        units.add(new NearbyUnit(dto.lat, dto.lng, dto.eta_min));
                    }
                }
                // Lista vacía, no null: "no hay unidades" es un resultado legítimo del contrato y
                // quien lo consume no tiene por qué distinguirlo de un cuerpo mal formado.
                callback.onSuccess(units);
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void createQuote(double originLat, double originLng, double destLat, double destLng,
                             String originText, String destText, List<Waypoint> waypoints,
                             ApiCallback<Quote> callback) {
        List<LatLngDto> waypointDtos = null;
        if (waypoints != null && !waypoints.isEmpty()) {
            waypointDtos = new ArrayList<>();
            for (Waypoint waypoint : waypoints) {
                waypointDtos.add(new LatLngDto(waypoint.getLat(), waypoint.getLng()));
            }
        }
        QuoteRequest body = new QuoteRequest(new LatLngDto(originLat, originLng),
                new LatLngDto(destLat, destLng), waypointDtos, originText, destText);
        ApiCallDispatcher.enqueue(service.createQuote(body), new ApiCallback<QuoteDto>() {
            @Override
            public void onSuccess(QuoteDto result) {
                callback.onSuccess(new Quote(result.id, result.suggested_fare, result.floor, result.ceiling));
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void createRide(String quoteId, double offer, ApiCallback<Ride> callback) {
        String idempotencyKey = UUID.randomUUID().toString();
        ApiCallDispatcher.enqueue(service.createRide(idempotencyKey, new CreateRideRequest(quoteId, offer)),
                new ApiCallback<RideDto>() {
                    @Override
                    public void onSuccess(RideDto result) {
                        callback.onSuccess(toRide(result));
                    }

                    @Override
                    public void onError(ApiException error) {
                        callback.onError(error);
                    }
                });
    }

    @Override
    public void getOffers(String rideId, ApiCallback<List<Offer>> callback) {
        ApiCallDispatcher.enqueue(service.getOffers(rideId), new ApiCallback<List<OfferCardDto>>() {
            @Override
            public void onSuccess(List<OfferCardDto> result) {
                List<Offer> offers = new ArrayList<>();
                if (result != null) {
                    for (OfferCardDto dto : result) {
                        offers.add(toOffer(dto));
                    }
                }
                callback.onSuccess(offers);
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Misma forma que arma {@code FirestoreRideRealtimeRepository} desde el canal en vivo: las dos
     * fuentes tienen que producir el mismo {@link Offer} o la pantalla vería dos ofertas distintas
     * según por dónde llegó la misma.
     */
    private Offer toOffer(OfferCardDto dto) {
        DriverSummaryDto driver = dto.driver;
        return new Offer(dto.offer_id,
                driver != null ? driver.name : null,
                driver != null ? driver.rating : null,
                driver != null ? driver.brand : null,
                driver != null ? driver.model : null,
                driver != null ? driver.color : null,
                driver != null ? driver.plate : null,
                dto.amount, dto.eta_min,
                dto.queue_position, dto.queue_total,
                parseInstantMillis(dto.expires_at));
    }

    /** El contrato manda los instantes en ISO-8601; el modelo los guarda en milisegundos. */
    private static Long parseInstantMillis(String isoTimestamp) {
        if (isoTimestamp == null) {
            return null;
        }
        try {
            return Instant.parse(isoTimestamp).toEpochMilli();
        } catch (DateTimeParseException malFormado) {
            return null;
        }
    }

    @Override
    public void acceptOffer(String rideId, String offerId, ApiCallback<Ride> callback) {
        String idempotencyKey = UUID.randomUUID().toString();
        ApiCallDispatcher.enqueue(
                service.acceptOffer(rideId, idempotencyKey, new OfferIdRequest(offerId)),
                new ApiCallback<RideDto>() {
                    @Override
                    public void onSuccess(RideDto result) {
                        callback.onSuccess(toRide(result));
                    }

                    @Override
                    public void onError(ApiException error) {
                        callback.onError(error);
                    }
                });
    }

    @Override
    public void rejectOffer(String rideId, String offerId, ApiCallback<Void> callback) {
        ApiCallDispatcher.enqueue(service.rejectOffer(rideId, new OfferIdRequest(offerId)), callback);
    }

    @Override
    public void cancelRide(String rideId, ApiCallback<Ride> callback) {
        ApiCallDispatcher.enqueue(service.cancelRide(rideId), new ApiCallback<RideDto>() {
            @Override
            public void onSuccess(RideDto result) {
                callback.onSuccess(toRide(result));
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void sendSos(String rideId, double lat, double lng, ApiCallback<String> callback) {
        ApiCallDispatcher.enqueue(service.sendSos(new SosRequest(rideId, lat, lng)), new ApiCallback<SosResponseDto>() {
            @Override
            public void onSuccess(SosResponseDto result) {
                callback.onSuccess(result.tracking_url);
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void rateRide(String rideId, int stars, String comment, ApiCallback<Void> callback) {
        ApiCallDispatcher.enqueue(service.rateRide(rideId, new RatingRequest(stars, comment)), callback);
    }

    @Override
    public void getRideHistory(int limit, ApiCallback<List<RideSummary>> callback) {
        ApiCallDispatcher.enqueue(service.getRides("passenger", limit), new ApiCallback<List<RideSummaryDto>>() {
            @Override
            public void onSuccess(List<RideSummaryDto> result) {
                List<RideSummary> summaries = new ArrayList<>();
                for (RideSummaryDto dto : result) {
                    summaries.add(new RideSummary(dto.id, dto.status, dto.agreed_fare,
                            dto.origin_text, dto.dest_text, dto.requested_at));
                }
                callback.onSuccess(summaries);
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void getRideDetail(String rideId, ApiCallback<Ride> callback) {
        ApiCallDispatcher.enqueue(service.getRide(rideId), new ApiCallback<RideDto>() {
            @Override
            public void onSuccess(RideDto result) {
                callback.onSuccess(toRide(result));
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    private Ride toRide(RideDto dto) {
        DriverSummaryDto driver = dto.driver;
        PlaceDto origin = dto.origin;
        PlaceDto destination = dto.destination;
        return new Ride(dto.id, dto.status, dto.agreed_fare,
                driver != null ? driver.name : null, driver != null ? driver.rating : null,
                driver != null ? driver.brand : null, driver != null ? driver.model : null,
                driver != null ? driver.color : null, driver != null ? driver.plate : null,
                origin != null ? origin.text : null, destination != null ? destination.text : null,
                origin != null ? origin.lat : null, origin != null ? origin.lng : null,
                destination != null ? destination.lat : null, destination != null ? destination.lng : null,
                dto.requested_at, dto.driver_arrived_at, dto.commission);
    }
}
