package com.bng.drivo.data.repository;

/**
 * Resultado de "Continuar con Google".
 *
 * <p>Tiene un caso más que {@link OtpVerifyCallback} porque aquí sí existe: la hoja de cuentas la
 * dibuja el sistema y el usuario puede cerrarla sin elegir. Cancelar no es un fallo y no debe
 * enseñar ningún error — quien lo trate como error le echa la culpa al usuario por cambiar de
 * opinión.
 */
public interface GoogleSignInCallback {

    /** Sesión de Firebase ya creada; a partir de aquí el flujo es idéntico al del OTP. */
    void onSuccess();

    /** El usuario cerró la hoja de cuentas. No hay nada que decirle. */
    void onCancelled();

    void onError(String message);
}
