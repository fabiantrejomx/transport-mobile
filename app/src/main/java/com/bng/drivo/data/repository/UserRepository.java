package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.remote.ApiCallback;

public interface UserRepository {

    /** GET /me */
    void getCurrentUser(ApiCallback<UserProfile> callback);

    /** POST /me — idempotente, crea o sincroniza el perfil tras el login. Sin body. */
    void syncProfile(ApiCallback<UserProfile> callback);

    /**
     * PATCH /me. Lo que llegue null no se toca.
     *
     * <p>El servidor rechaza con 403 el campo que verificó el proveedor de identidad: el correo de
     * una cuenta de Google y el teléfono de una creada por SMS. Quien llame debe mandar null en
     * ese campo — ver {@code UserProfile.isGoogleAccount()}.
     *
     * @param phone en formato E.164 (+52...), y solo para cuentas creadas con Google.
     */
    void updateProfile(String name, String email, String phone, ApiCallback<UserProfile> callback);
}
