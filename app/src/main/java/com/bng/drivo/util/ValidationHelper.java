package com.bng.drivo.util;

import android.util.Patterns;

public final class ValidationHelper {

    private ValidationHelper() {
    }

    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static boolean isValidEmail(String email) {
        return isNotEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    /** Por ahora solo se aceptan números mexicanos: 10 dígitos, sin el +52 (va fijo aparte). */
    public static boolean isValidMexicanPhone(String tenDigits) {
        return tenDigits != null && tenDigits.matches("\\d{10}");
    }
}
