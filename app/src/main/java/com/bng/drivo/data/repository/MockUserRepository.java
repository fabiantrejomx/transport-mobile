package com.bng.drivo.data.repository;

import android.content.Context;

import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.util.PrefsHelper;

public class MockUserRepository implements UserRepository {

    private static final String KEY_NAME = "user_name";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_PHONE = "user_phone";
    private static final String KEY_RATING = "user_rating";

    private final PrefsHelper prefsHelper;

    public MockUserRepository(Context context) {
        this.prefsHelper = new PrefsHelper(context);
    }

    @Override
    public UserProfile getCurrentUser() {
        String name = prefsHelper.getString(KEY_NAME, null);
        if (name == null) {
            return null;
        }
        String email = prefsHelper.getString(KEY_EMAIL, "");
        String phone = prefsHelper.getString(KEY_PHONE, "");
        String rating = prefsHelper.getString(KEY_RATING, "4.9");
        return new UserProfile(name, email, phone, rating);
    }

    @Override
    public void saveCurrentUser(UserProfile profile) {
        prefsHelper.putString(KEY_NAME, profile.getName());
        prefsHelper.putString(KEY_EMAIL, profile.getEmail());
        prefsHelper.putString(KEY_PHONE, profile.getPhone());
        prefsHelper.putString(KEY_RATING, profile.getRating());
    }

    @Override
    public void clearCurrentUser() {
        prefsHelper.remove(KEY_NAME);
        prefsHelper.remove(KEY_EMAIL);
        prefsHelper.remove(KEY_PHONE);
        prefsHelper.remove(KEY_RATING);
    }
}
