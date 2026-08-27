package com.bng.drivo.util;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Baja la cabecera del drawer por debajo de la status bar.
 *
 * <p>Hace falta porque NavigationView deja de compensar el inset superior en cuanto el cajón
 * tiene cabecera: su presenter solo empuja la lista de items {@code (headerLayout.getChildCount()
 * == 0)}, dando por hecho que una cabecera propia se encarga ella misma. Con la app dibujando de
 * borde a borde, eso dejaba el nombre del usuario justo debajo del reloj.
 *
 * <p>Se aplica al empezar a abrirse el cajón (no en onCreate): ahí los insets ya están
 * disponibles con seguridad, y como el padding solo se toca cuando cambia de verdad, repetir la
 * llamada en cada fotograma del gesto no cuesta nada.
 */
public final class DrawerInsets {

    private DrawerInsets() {
    }

    /** @param basePaddingPx el padding superior propio de la cabecera, al que se suma el inset. */
    public static void applyTopInset(View header, int basePaddingPx) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(header);
        if (insets == null) {
            return;
        }
        int topInsetPx = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top;
        int targetPaddingPx = topInsetPx + basePaddingPx;
        if (header.getPaddingTop() != targetPaddingPx) {
            header.setPadding(header.getPaddingLeft(), targetPaddingPx,
                    header.getPaddingRight(), header.getPaddingBottom());
        }
    }
}
