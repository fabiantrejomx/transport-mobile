package com.bng.drivo.data.repository;

import android.content.Context;

import com.bng.drivo.data.model.Quote;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.remote.ApiCallDispatcher;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiClient;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.remote.TransportApiService;
import com.bng.drivo.data.remote.dto.CreateRideRequest;
import com.bng.drivo.data.remote.dto.DriverSummaryDto;
import com.bng.drivo.data.remote.dto.LatLngDto;
import com.bng.drivo.data.remote.dto.OfferIdRequest;
import com.bng.drivo.data.remote.dto.QuoteDto;
import com.bng.drivo.data.remote.dto.QuoteRequest;
import com.bng.drivo.data.remote.dto.RideDto;
import com.bng.drivo.data.remote.dto.SosRequest;
import com.bng.drivo.data.remote.dto.SosResponseDto;

import java.util.UUID;

public class RestTripRepository implements TripRepository {

    private final TransportApiService service;

    public RestTripRepository(Context context) {
        this.service = ApiClient.getService(context);
    }

    @Override
    public void createQuote(double originLat, double originLng, double destLat, double destLng,
                             String originText, String destText, ApiCallback<Quote> callback) {
        QuoteRequest body = new QuoteRequest(new LatLngDto(originLat, originLng),
                new LatLngDto(destLat, destLng), null, originText, destText);
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

    private Ride toRide(RideDto dto) {
        DriverSummaryDto driver = dto.driver;
        return new Ride(dto.id, dto.status, dto.agreed_fare,
                driver != null ? driver.name : null, driver != null ? driver.rating : null,
                driver != null ? driver.brand : null, driver != null ? driver.model : null,
                driver != null ? driver.color : null, driver != null ? driver.plate : null);
    }
}
