package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.remote.ApiCallback;

import java.util.List;

public interface AddressRepository {

    /** GET /favorites */
    void getAll(ApiCallback<List<SavedAddress>> callback);

    /**
     * POST /favorites. El servidor exige que el {@code label} no se repita dentro del mismo
     * usuario (sin distinguir mayúsculas ni espacios sobrantes): un nombre ocupado responde
     * {@code FAVORITE_LABEL_TAKEN}.
     */
    void create(String label, String addressText, double lat, double lng, ApiCallback<SavedAddress> callback);

    /**
     * PATCH /favorites/{id} — cambio parcial: lo que va null se queda como está, y las
     * coordenadas van juntas o no van. Misma regla de nombres únicos que {@link #create}.
     */
    void update(String id, String label, String addressText, Double lat, Double lng,
                 ApiCallback<SavedAddress> callback);

    /** DELETE /favorites/{id} */
    void delete(String id, ApiCallback<Void> callback);
}
