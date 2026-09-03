package com.bng.drivo.data.remote.dto;

/**
 * Cuerpo de {@code PATCH /me}. Los campos nulos no se mandan y el servidor deja intacto lo que no
 * llega, así que sirve para actualizar uno solo.
 *
 * <p><b>Mandar un campo bloqueado es un error 403</b>, no un campo ignorado: quien construya esto
 * tiene que dejar en null el correo de una cuenta de Google y el teléfono de una de SMS. Ver
 * {@code UserProfile.isGoogleAccount()}.
 */
public class UpdateMeRequest {
    public String name;
    public String email;
    public String photo_url;

    /** Formato E.164 (+52...). Solo lo acepta una cuenta creada con Google. */
    public String phone;

    public UpdateMeRequest(String name, String email, String photoUrl, String phone) {
        this.name = name;
        this.email = email;
        this.photo_url = photoUrl;
        this.phone = phone;
    }
}
