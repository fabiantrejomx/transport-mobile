package com.bng.drivo.util;

import androidx.annotation.Nullable;

/**
 * Traduce entre las dos formas del mismo número: la que se guarda y la que se escribe.
 *
 * <p>El backend guarda E.164 ({@code +525512345678}) porque es lo que deja Firebase al verificar
 * un teléfono por SMS, y las dos vías de alta tienen que dejar el mismo dato en la misma columna o
 * el índice único no vería como repetido el mismo número. Los campos de la app, en cambio, pintan
 * el prefijo aparte y solo piden los diez dígitos.
 */
public final class PhoneNumbers {

    /** México. El piloto es de Córdoba y Orizaba; no hay marcación internacional que resolver. */
    public static final String COUNTRY_PREFIX = "+52";

    private PhoneNumbers() {
    }

    /** Los diez dígitos que se escriben en el campo, o cadena vacía si no hay número guardado. */
    public static String toLocalDigits(@Nullable String e164) {
        if (e164 == null) {
            return "";
        }
        String trimmed = e164.trim();
        return trimmed.startsWith(COUNTRY_PREFIX)
                ? trimmed.substring(COUNTRY_PREFIX.length())
                : trimmed;
    }

    public static String toE164(String tenDigits) {
        return COUNTRY_PREFIX + tenDigits;
    }
}
