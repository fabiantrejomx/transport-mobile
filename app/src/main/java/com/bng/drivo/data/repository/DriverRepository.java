package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.DriverApplication;
import com.bng.drivo.data.model.IncomingRequest;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.Wallet;
import com.bng.drivo.data.remote.ApiCallback;

public interface DriverRepository {

    /** GET /driver/application */
    void getApplication(ApiCallback<DriverApplication> callback);

    /** POST /driver/application — la modalidad determina qué documentos se exigen. */
    void submitApplication(String modality, String curp, String rfc, String vehicleBrand, String vehicleModel,
                            String vehicleColor, String vehiclePlate, int vehicleYear, boolean isOwner,
                            ApiCallback<DriverApplication> callback);

    /** POST /driver/documents — solo registra la ruta; la foto ya se subió antes a Cloud Storage. */
    void registerDocument(String type, String storagePath, ApiCallback<Void> callback);

    /** POST /driver/online — rechaza si no está aprobado o el saldo está bajo el mínimo. */
    void goOnline(ApiCallback<Void> callback);

    /** POST /driver/offline */
    void goOffline(ApiCallback<Void> callback);

    /** POST /driver/location — el servidor rechaza envíos con menos de 3s de separación. */
    void reportLocation(double lat, double lng, Double heading, Double accuracyM, ApiCallback<Void> callback);

    /** GET /driver/rides/{id} */
    void getIncomingRequest(String rideId, ApiCallback<IncomingRequest> callback);

    /** POST /driver/rides/{id}/offer — amount igual a la oferta del pasajero acepta, mayor contraoferta. */
    void offerOnRide(String rideId, double amount, ApiCallback<Void> callback);

    /** POST /driver/rides/{id}/arrived — el servidor valida por GPS que esté a <150m del origen. */
    void markArrived(String rideId, double lat, double lng, ApiCallback<Ride> callback);

    /** POST /driver/rides/{id}/start */
    void startRide(String rideId, ApiCallback<Ride> callback);

    /** POST /driver/rides/{id}/complete — dispara la comisión, solo el conductor cierra el viaje. */
    void completeRide(String rideId, ApiCallback<Ride> callback);

    /** POST /driver/rides/{id}/cancel */
    void cancelRide(String rideId, ApiCallback<Ride> callback);

    /** POST /driver/rides/{id}/rating — calificar al pasajero. */
    void rateRide(String rideId, int stars, String comment, ApiCallback<Void> callback);

    /** GET /driver/wallet */
    void getWallet(ApiCallback<Wallet> callback);
}
