package com.bng.drivo.data.repository;

import android.content.Context;

import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.util.PrefsHelper;

/**
 * Implementación mock de autenticación: no valida contra ningún backend real.
 * Sólo la credencial demo ({@link #DEMO_EMAIL} / {@link #DEMO_PASSWORD}) puede
 * iniciar sesión; el registro acepta cualquier dato bien formado (validado por
 * {@link com.bng.drivo.util.ValidationHelper} antes de llegar aquí) y crea una
 * sesión local. Sustituir por FirebaseAuthRepository cuando exista backend real.
 */
public class MockAuthRepository implements AuthRepository {

    public static final String DEMO_EMAIL = "pasajero@drivo.mx";
    public static final String DEMO_PASSWORD = "root";

    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String DEMO_USER_NAME = "Alfonso";
    private static final String DEMO_USER_PHONE = "+52 55 0000 0000";
    private static final String DEMO_USER_RATING = "4.9";

    private final PrefsHelper prefsHelper;
    private final UserRepository userRepository;

    public MockAuthRepository(Context context) {
        this.prefsHelper = new PrefsHelper(context);
        this.userRepository = new MockUserRepository(context);
    }

    @Override
    public boolean isLoggedIn() {
        return prefsHelper.getBoolean(KEY_LOGGED_IN, false);
    }

    @Override
    public boolean login(String email, String password) {
        if (email == null || password == null) {
            return false;
        }
        boolean valid = email.trim().equalsIgnoreCase(DEMO_EMAIL) && password.equals(DEMO_PASSWORD);
        if (!valid) {
            return false;
        }
        userRepository.saveCurrentUser(
                new UserProfile(DEMO_USER_NAME, DEMO_EMAIL, DEMO_USER_PHONE, DEMO_USER_RATING));
        prefsHelper.putBoolean(KEY_LOGGED_IN, true);
        return true;
    }

    @Override
    public boolean register(String name, String email, String phone, String password) {
        userRepository.saveCurrentUser(new UserProfile(name, email, phone, "5.0"));
        prefsHelper.putBoolean(KEY_LOGGED_IN, true);
        return true;
    }

    @Override
    public void logout() {
        prefsHelper.putBoolean(KEY_LOGGED_IN, false);
        userRepository.clearCurrentUser();
    }
}
