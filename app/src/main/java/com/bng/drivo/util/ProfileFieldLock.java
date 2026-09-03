package com.bng.drivo.util;

import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.bng.drivo.R;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Dibuja el candado del campo que verificó el proveedor de identidad: el teléfono de una cuenta
 * creada por SMS, el correo de una creada con Google. Ver {@code UserProfile.isGoogleAccount()}.
 *
 * <p>Esto es <b>solo la cortesía visual</b>. Quien impide de verdad el cambio es
 * {@code PATCH /me}, que responde 403 si le llega el campo bloqueado — un EditText deshabilitado
 * no detiene a nadie que mande el JSON a mano, y en el caso del teléfono ese candado protege el
 * número con el que el conductor llama al pasajero.
 *
 * <p>El campo no se deshabilita: se deja de poder escribir en él, pero conserva el contraste
 * normal. Es un dato que el usuario viene justo a <b>leer</b> —su propio correo, su propio
 * número— y en gris de "inactivo" parecería que no cargó. El icono y el texto de ayuda explican
 * por qué no se toca, que es lo que evita que lo intente y no entienda qué pasa.
 */
public final class ProfileFieldLock {

    private ProfileFieldLock() {
    }

    /** El correo lo verificó Google. */
    public static void lockEmail(@NonNull TextInputLayout layout, @Nullable String email) {
        lock(layout, email, R.string.perfil_edit_email_locked);
    }

    /** El teléfono lo verificó un SMS. */
    public static void lockPhone(@NonNull TextInputLayout layout, @Nullable String phone) {
        lock(layout, phone, R.string.perfil_edit_phone_locked);
    }

    private static void lock(TextInputLayout layout, String value, @StringRes int reason) {
        EditText input = layout.getEditText();
        if (input == null) {
            return;
        }
        input.setText(value);
        input.setFocusable(false);
        input.setCursorVisible(false);
        input.setLongClickable(false);
        // Sin esto el campo sigue robándose el toque y abriendo el teclado sobre un campo que no
        // acepta nada, que es peor que no reaccionar.
        input.setClickable(false);

        layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layout.setEndIconDrawable(R.drawable.ic_lock);
        layout.setEndIconContentDescription(reason);
        layout.setHelperText(layout.getContext().getString(reason));
    }
}
