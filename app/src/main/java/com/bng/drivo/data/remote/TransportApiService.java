package com.bng.drivo.data.remote;

import com.bng.drivo.data.remote.dto.ApplicationStatusDto;
import com.bng.drivo.data.remote.dto.CreateRideRequest;
import com.bng.drivo.data.remote.dto.DeviceRegisterRequest;
import com.bng.drivo.data.remote.dto.DriverApplicationRequest;
import com.bng.drivo.data.remote.dto.DriverAtLocationRequest;
import com.bng.drivo.data.remote.dto.DriverDocumentRequest;
import com.bng.drivo.data.remote.dto.DriverLocationRequest;
import com.bng.drivo.data.remote.dto.DriverOfferRequest;
import com.bng.drivo.data.remote.dto.FavoriteCreateRequest;
import com.bng.drivo.data.remote.dto.FavoriteDto;
import com.bng.drivo.data.remote.dto.FavoritePatchRequest;
import com.bng.drivo.data.remote.dto.IncomingRequestDto;
import com.bng.drivo.data.remote.dto.MeDto;
import com.bng.drivo.data.remote.dto.NearbyDriversDto;
import com.bng.drivo.data.remote.dto.OfferCardDto;
import com.bng.drivo.data.remote.dto.OfferIdRequest;
import com.bng.drivo.data.remote.dto.QuoteDto;
import com.bng.drivo.data.remote.dto.QuoteRequest;
import com.bng.drivo.data.remote.dto.RatingRequest;
import com.bng.drivo.data.remote.dto.RideDto;
import com.bng.drivo.data.remote.dto.RideSummaryDto;
import com.bng.drivo.data.remote.dto.SosRequest;
import com.bng.drivo.data.remote.dto.SosResponseDto;
import com.bng.drivo.data.remote.dto.UpdateMeRequest;
import com.bng.drivo.data.remote.dto.WalletDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Endpoints de transport-api ("LibreGO — API del piloto" v1.1.0) usados por la app de
 * pasajero/conductor. No incluye {@code /admin/*}: el propio contrato dice que esas rutas son
 * para el backoffice, no se consumen desde el móvil.
 */
public interface TransportApiService {

    // ------------------------------------------------------------------------------- Perfil

    @GET("/me")
    Call<MeDto> getMe();

    @POST("/me")
    Call<MeDto> createMe();

    @PATCH("/me")
    Call<MeDto> updateMe(@Body UpdateMeRequest body);

    @POST("/devices")
    Call<Void> registerDevice(@Body DeviceRegisterRequest body);

    // ----------------------------------------------------------------------------- Pasajero

    /**
     * Foto aproximada de la zona (hasta tres unidades), no un radar: no hay difusión en vivo de
     * los conductores libres. Un {@code drivers} vacío es una respuesta normal, no un error.
     */
    @GET("/nearby-drivers")
    Call<NearbyDriversDto> getNearbyDrivers(@Query("lat") double lat, @Query("lng") double lng);

    @POST("/quotes")
    Call<QuoteDto> createQuote(@Body QuoteRequest body);

    @GET("/rides")
    Call<List<RideSummaryDto>> getRides(@Query("role") String role, @Query("limit") Integer limit);

    @POST("/rides")
    Call<RideDto> createRide(@Header("Idempotency-Key") String idempotencyKey, @Body CreateRideRequest body);

    @GET("/rides/{id}")
    Call<RideDto> getRide(@Path("id") String rideId);

    @GET("/rides/{id}/offers")
    Call<List<OfferCardDto>> getOffers(@Path("id") String rideId);

    @POST("/rides/{id}/accept-offer")
    Call<RideDto> acceptOffer(@Path("id") String rideId, @Header("Idempotency-Key") String idempotencyKey,
                               @Body OfferIdRequest body);

    @POST("/rides/{id}/reject-offer")
    Call<Void> rejectOffer(@Path("id") String rideId, @Body OfferIdRequest body);

    @POST("/rides/{id}/cancel")
    Call<RideDto> cancelRide(@Path("id") String rideId);

    @POST("/rides/{id}/rating")
    Call<Void> rateRide(@Path("id") String rideId, @Body RatingRequest body);

    @GET("/favorites")
    Call<List<FavoriteDto>> getFavorites();

    @POST("/favorites")
    Call<FavoriteDto> createFavorite(@Header("Idempotency-Key") String idempotencyKey,
                                      @Body FavoriteCreateRequest body);

    @PATCH("/favorites/{id}")
    Call<FavoriteDto> updateFavorite(@Path("id") String favoriteId, @Body FavoritePatchRequest body);

    @DELETE("/favorites/{id}")
    Call<Void> deleteFavorite(@Path("id") String favoriteId);

    @POST("/sos")
    Call<SosResponseDto> sendSos(@Body SosRequest body);

    // ---------------------------------------------------------------------------- Conductor

    @GET("/driver/application")
    Call<ApplicationStatusDto> getDriverApplication();

    @POST("/driver/application")
    Call<ApplicationStatusDto> submitDriverApplication(@Body DriverApplicationRequest body);

    @POST("/driver/documents")
    Call<Void> registerDriverDocument(@Body DriverDocumentRequest body);

    @POST("/driver/online")
    Call<Void> setDriverOnline();

    @POST("/driver/offline")
    Call<Void> setDriverOffline();

    @POST("/driver/location")
    Call<Void> reportDriverLocation(@Body DriverLocationRequest body);

    /** 204 sin cuerpo cuando el conductor no trae viaje asignado. */
    @GET("/driver/current-ride")
    Call<RideDto> getDriverCurrentRide();

    @GET("/driver/rides/{id}")
    Call<IncomingRequestDto> getIncomingRide(@Path("id") String rideId);

    @POST("/driver/rides/{id}/offer")
    Call<Void> offerOnRide(@Path("id") String rideId, @Header("Idempotency-Key") String idempotencyKey,
                            @Body DriverOfferRequest body);

    @POST("/driver/rides/{id}/arrived")
    Call<RideDto> markDriverArrived(@Path("id") String rideId, @Body DriverAtLocationRequest body);

    @POST("/driver/rides/{id}/start")
    Call<RideDto> startRide(@Path("id") String rideId);

    @POST("/driver/rides/{id}/complete")
    Call<RideDto> completeRide(@Path("id") String rideId, @Header("Idempotency-Key") String idempotencyKey,
                               @Body DriverAtLocationRequest body);

    @POST("/driver/rides/{id}/cancel")
    Call<RideDto> cancelRideAsDriver(@Path("id") String rideId);

    @POST("/driver/rides/{id}/rating")
    Call<Void> rateRideAsDriver(@Path("id") String rideId, @Body RatingRequest body);

    @GET("/driver/wallet")
    Call<WalletDto> getWallet();
}
