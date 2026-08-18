package com.bng.drivo.data.model;

import android.content.Context;

import com.bng.drivo.R;

/**
 * Preset visual (ícono + nombre) usado solo del lado del cliente. El servidor guarda
 * {@code SavedAddress.label} como texto libre; este enum solo sirve para mapear ese texto a
 * un ícono en la UI y para poblar los 3 chips del formulario de alta/edición.
 */
public enum AddressLabel {

    CASA(R.string.address_label_casa, "🏠"),
    TRABAJO(R.string.address_label_trabajo, "💼"),
    OTRO(R.string.address_label_otro, "📍");

    private final int displayNameRes;
    private final String emoji;

    AddressLabel(int displayNameRes, String emoji) {
        this.displayNameRes = displayNameRes;
        this.emoji = emoji;
    }

    public int getDisplayNameRes() {
        return displayNameRes;
    }

    public String getEmoji() {
        return emoji;
    }

    public static AddressLabel fromText(Context context, String text) {
        if (text != null) {
            if (text.equalsIgnoreCase(context.getString(R.string.address_label_casa))) {
                return CASA;
            }
            if (text.equalsIgnoreCase(context.getString(R.string.address_label_trabajo))) {
                return TRABAJO;
            }
        }
        return OTRO;
    }
}
