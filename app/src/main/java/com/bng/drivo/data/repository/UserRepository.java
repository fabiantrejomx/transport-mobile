package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.UserProfile;

public interface UserRepository {

    UserProfile getCurrentUser();

    void saveCurrentUser(UserProfile profile);

    void clearCurrentUser();
}
