package com.bng.drivo.data.repository;

import android.content.Context;

import com.bng.drivo.data.model.DriverApplication;
import com.bng.drivo.data.model.IncomingRequest;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.Wallet;
import com.bng.drivo.data.remote.ApiCallDispatcher;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiClient;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.remote.TransportApiService;
import com.bng.drivo.data.remote.dto.ApplicationStatusDto;
import com.bng.drivo.data.remote.dto.DriverApplicationRequest;
import com.bng.drivo.data.remote.dto.DriverArrivedRequest;
import com.bng.drivo.data.remote.dto.DriverDocumentRequest;
import com.bng.drivo.data.remote.dto.DriverLocationRequest;
import com.bng.drivo.data.remote.dto.DriverOfferRequest;
import com.bng.drivo.data.remote.dto.DriverSummaryDto;
import com.bng.drivo.data.remote.dto.IncomingRequestDto;
import com.bng.drivo.data.remote.dto.PlaceDto;
import com.bng.drivo.data.remote.dto.RideDto;
import com.bng.drivo.data.remote.dto.WalletDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RestDriverRepository implements DriverRepository {

    private final TransportApiService service;

    public RestDriverRepository(Context context) {
        this.service = ApiClient.getService(context);
    }

    @Override
    public void getApplication(ApiCallback<DriverApplication> callback) {
        ApiCallDispatcher.enqueue(service.getDriverApplication(), mapApplication(callback));
    }

    @Override
    public void submitApplication(String modality, String curp, String rfc, String vehicleBrand,
                                   String vehicleModel, String vehicleColor, String vehiclePlate, int vehicleYear,
                                   boolean isOwner, ApiCallback<DriverApplication> callback) {
        DriverApplicationRequest.Vehicle vehicle = new DriverApplicationRequest.Vehicle(
                vehicleBrand, vehicleModel, vehicleColor, vehiclePlate, vehicleYear, isOwner);
        DriverApplicationRequest body = new DriverApplicationRequest(modality, curp, rfc, vehicle);
        ApiCallDispatcher.enqueue(service.submitDriverApplication(body), mapApplication(callback));
    }

    @Override
    public void registerDocument(String type, String storagePath, ApiCallback<Void> callback) {
        ApiCallDispatcher.enqueue(service.registerDriverDocument(new DriverDocumentRequest(type, storagePath)),
                callback);
    }

    @Override
    public void goOnline(ApiCallback<Void> callback) {
        ApiCallDispatcher.enqueue(service.setDriverOnline(), callback);
    }

    @Override
    public void goOffline(ApiCallback<Void> callback) {
        ApiCallDispatcher.enqueue(service.setDriverOffline(), callback);
    }

    @Override
    public void reportLocation(double lat, double lng, Double heading, Double accuracyM,
                                ApiCallback<Void> callback) {
        ApiCallDispatcher.enqueue(
                service.reportDriverLocation(new DriverLocationRequest(lat, lng, heading, accuracyM)), callback);
    }

    @Override
    public void getIncomingRequest(String rideId, ApiCallback<IncomingRequest> callback) {
        ApiCallDispatcher.enqueue(service.getIncomingRide(rideId), new ApiCallback<IncomingRequestDto>() {
            @Override
            public void onSuccess(IncomingRequestDto dto) {
                callback.onSuccess(toIncomingRequest(dto));
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void offerOnRide(String rideId, double amount, ApiCallback<Void> callback) {
        String idempotencyKey = UUID.randomUUID().toString();
        ApiCallDispatcher.enqueue(
                service.offerOnRide(rideId, idempotencyKey, new DriverOfferRequest(amount)), callback);
    }

    @Override
    public void markArrived(String rideId, double lat, double lng, ApiCallback<Ride> callback) {
        ApiCallDispatcher.enqueue(service.markDriverArrived(rideId, new DriverArrivedRequest(lat, lng)),
                mapRide(callback));
    }

    @Override
    public void startRide(String rideId, ApiCallback<Ride> callback) {
        ApiCallDispatcher.enqueue(service.startRide(rideId), mapRide(callback));
    }

    @Override
    public void completeRide(String rideId, ApiCallback<Ride> callback) {
        String idempotencyKey = UUID.randomUUID().toString();
        ApiCallDispatcher.enqueue(service.completeRide(rideId, idempotencyKey), mapRide(callback));
    }

    @Override
    public void cancelRide(String rideId, ApiCallback<Ride> callback) {
        ApiCallDispatcher.enqueue(service.cancelRideAsDriver(rideId), mapRide(callback));
    }

    @Override
    public void rateRide(String rideId, int stars, String comment, ApiCallback<Void> callback) {
        ApiCallDispatcher.enqueue(
                service.rateRideAsDriver(rideId, new com.bng.drivo.data.remote.dto.RatingRequest(stars, comment)),
                callback);
    }

    @Override
    public void getWallet(ApiCallback<Wallet> callback) {
        ApiCallDispatcher.enqueue(service.getWallet(), new ApiCallback<WalletDto>() {
            @Override
            public void onSuccess(WalletDto dto) {
                List<Wallet.WalletEntry> entries = new ArrayList<>();
                if (dto.entries != null) {
                    for (WalletDto.WalletEntryDto entryDto : dto.entries) {
                        entries.add(new Wallet.WalletEntry(entryDto.type, entryDto.amount, entryDto.note,
                                entryDto.created_at));
                    }
                }
                callback.onSuccess(new Wallet(dto.balance, entries));
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        });
    }

    private ApiCallback<ApplicationStatusDto> mapApplication(ApiCallback<DriverApplication> callback) {
        return new ApiCallback<ApplicationStatusDto>() {
            @Override
            public void onSuccess(ApplicationStatusDto dto) {
                callback.onSuccess(new DriverApplication(dto.status, dto.modality, dto.required_documents,
                        dto.missing_documents, dto.rejection_reason));
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        };
    }

    private ApiCallback<RideDto> mapRide(ApiCallback<Ride> callback) {
        return new ApiCallback<RideDto>() {
            @Override
            public void onSuccess(RideDto dto) {
                DriverSummaryDto driver = dto.driver;
                PlaceDto origin = dto.origin;
                PlaceDto destination = dto.destination;
                callback.onSuccess(new Ride(dto.id, dto.status, dto.agreed_fare,
                        driver != null ? driver.name : null, driver != null ? driver.rating : null,
                        driver != null ? driver.brand : null, driver != null ? driver.model : null,
                        driver != null ? driver.color : null, driver != null ? driver.plate : null,
                        origin != null ? origin.text : null, destination != null ? destination.text : null,
                        origin != null ? origin.lat : null, origin != null ? origin.lng : null,
                        destination != null ? destination.lat : null, destination != null ? destination.lng : null,
                        dto.requested_at, dto.commission));
            }

            @Override
            public void onError(ApiException error) {
                callback.onError(error);
            }
        };
    }

    private IncomingRequest toIncomingRequest(IncomingRequestDto dto) {
        String passengerName = dto.passenger != null ? dto.passenger.name : null;
        Double passengerRating = dto.passenger != null ? dto.passenger.rating : null;
        Integer passengerTrips = dto.passenger != null ? dto.passenger.trips : null;
        return new IncomingRequest(dto.ride_id, passengerName, passengerRating, passengerTrips, dto.offer,
                dto.pickup != null ? dto.pickup.text : null, dto.dropoff != null ? dto.dropoff.text : null,
                dto.pickup != null ? dto.pickup.lat : null, dto.pickup != null ? dto.pickup.lng : null,
                dto.dropoff != null ? dto.dropoff.lat : null, dto.dropoff != null ? dto.dropoff.lng : null,
                dto.pickup_distance_m, dto.pickup_eta_min, dto.trip_distance_m, dto.counter_increments,
                dto.expires_at);
    }
}
