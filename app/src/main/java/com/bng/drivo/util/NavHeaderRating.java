package com.bng.drivo.util;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bng.drivo.R;

/**
 * Pinta la pastilla de calificación sobre el avatar de la cabecera del cajón
 * (part_nav_avatar_rating.xml). Vive aquí, y no en cada app, porque el pasajero y el conductor
 * enseñan el mismo dato con el mismo aspecto: separar el pintado sería separar las dos pastillas a
 * la primera corrección de texto.
 *
 * <p>Tres estados, y los tres importan:
 * <ul>
 *   <li><b>Con promedio</b> → "★ 4.9". Siempre un decimal, incluso en 5.0: es una media, y un "5"
 *       pelado se lee como una etiqueta y no como un promedio.</li>
 *   <li><b>Sin viajes calificados</b> ({@code trips == 0}) → "Nuevo". El 5.0 por omisión que da el
 *       backend a quien todavía no tiene estrellas es relleno, no un logro, y enseñarlo sería
 *       presumir de algo que nadie ha dado. Mismo criterio que el "(Nuevo)" que ya ve el conductor
 *       en las solicitudes entrantes.</li>
 *   <li><b>Sin dato</b> ({@code rating == null}) → la pastilla desaparece del todo. Ni "—" ni hueco
 *       reservado: en una cabecera que se abre y se cierra a cada rato, un guion que unas veces es
 *       número y otras no, parpadea.</li>
 * </ul>
 */
public final class NavHeaderRating {

    private NavHeaderRating() {
    }

    /**
     * @param rating promedio, o null si no se conoce (no es lo mismo que "no tiene").
     * @param trips  viajes que cuentan para el promedio, o null si el origen del dato no lo sabe —
     *               entonces se pinta el número, que es lo único que se puede afirmar.
     */
    public static void apply(@NonNull View header, @Nullable Double rating, @Nullable Integer trips) {
        TextView badge = header.findViewById(R.id.text_nav_rating);
        if (badge == null) {
            return;
        }
        if (rating == null) {
            badge.setVisibility(View.GONE);
            return;
        }
        badge.setText(text(badge.getContext(), rating, trips));
        badge.setVisibility(View.VISIBLE);
    }

    /**
     * La misma decisión que {@link #apply}, para pantallas que no usan una pastilla que se oculta
     * — como las tarjetas "Mi desempeño" de Configuración, que siempre muestran algo. Separada de
     * {@code apply} para que las dos vistas de esa tarjeta (pasajero y conductor) no puedan
     * contradecir a la del cajón sobre cuándo alguien es "Nuevo".
     */
    public static String text(@NonNull Context context, double rating, @Nullable Integer trips) {
        return trips != null && trips == 0
                ? context.getString(R.string.nav_rating_new)
                : context.getString(R.string.rating_star_format, rating);
    }
}
