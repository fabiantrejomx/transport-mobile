package com.bng.drivo.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Baja un toolbar por debajo de la status bar, y sube el fondo de un contenido desplazable por
 * encima de la barra de navegación, en pantallas simples de "toolbar + ScrollView".
 *
 * <p>{@code android:fitsSystemWindows="true"} en el root del layout debería bastar para esto,
 * pero no es fiable en esta Activity: sus fragments (Inicio/Viajes/Configuración) viven todos
 * añadidos de una vez y se conmutan con {@code show()}/{@code hide()} en vez de reemplazarse, y
 * ese mismo patrón ya le rompió el inset a HomeFragment (ver su {@code applyTopInsets}) — aquí el
 * síntoma fue el toolbar pintándose detrás de la status bar en Configuración pero no en Viajes,
 * con layouts idénticos, en Android 10. El listener explícito no depende de ese orden de despacho.
 */
public final class ToolbarInsets {

    private ToolbarInsets() {
    }

    /**
     * @param view un toolbar de alto fijo ({@code ?attr/actionBarSize}). El inset se suma a ese
     *             alto en vez de restarlo del padding disponible — meterlo solo como paddingTop
     *             encoge la banda donde caben el ícono y el título y los recorta contra el borde
     *             inferior del toolbar. Al crecer el alto, el padding empuja el contenido hacia
     *             abajo y le sobra la misma banda de siempre para dibujarse completo.
     */
    public static void applyTop(View view) {
        int left = view.getPaddingLeft();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        int baseHeightPx = view.getLayoutParams().height;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int top = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top;
            v.setPadding(left, top, right, bottom);
            if (baseHeightPx > 0) {
                ViewGroup.LayoutParams params = v.getLayoutParams();
                int targetHeightPx = baseHeightPx + top;
                if (params.height != targetHeightPx) {
                    params.height = targetHeightPx;
                    v.setLayoutParams(params);
                }
            }
            return insets;
        });
    }

    /** @param view recibe el inset inferior (barra de navegación) como paddingBottom. */
    public static void applyBottom(View view) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(left, top, right, bottom);
            return insets;
        });
    }
}
