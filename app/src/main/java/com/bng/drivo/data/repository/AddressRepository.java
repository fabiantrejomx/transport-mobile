package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.remote.ApiCallback;

import java.util.List;

public interface AddressRepository {

    /** GET /favorites */
    void getAll(ApiCallback<List<SavedAddress>> callback);

    /** POST /favorites — el contrato no tiene endpoint de edición, solo alta y borrado. */
    void create(String label, String addressText, double lat, double lng, ApiCallback<SavedAddress> callback);

    /** DELETE /favorites/{id} */
    void delete(String id, ApiCallback<Void> callback);
}
