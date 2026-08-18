package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.SavedAddress;

import java.util.List;

public interface AddressRepository {

    List<SavedAddress> getAll();

    void save(SavedAddress address);

    void delete(String id);
}
