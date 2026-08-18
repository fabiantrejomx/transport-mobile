package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;

public interface UserRepository {

    /** GET /me */
    void getCurrentUser(ApiCallback<UserProfile> callback);

    /** POST /me — idempotente, crea o sincroniza el perfil tras el login. Sin body. */
    void syncProfile(ApiCallback<UserProfile> callback);

    /** PATCH /me */
    void updateProfile(String name, String email, ApiCallback<UserProfile> callback);
}
