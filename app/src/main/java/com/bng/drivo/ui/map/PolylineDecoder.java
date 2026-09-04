package com.bng.drivo.ui.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Convierte el campo {@code polyline} que manda la API —el trazo de la ruta por calles, codificado
 * con el algoritmo de polilíneas de Google— en los puntos que el mapa sabe dibujar.
 *
 * <p>Es lo único que hace, y a propósito: el contrato es explícito en que el cliente no calcula
 * recorrido. Aquí no se decide por dónde va la ruta ni cuánto mide — eso ya lo resolvió el
 * servidor, que pagó la llamada a Google. Esto solo desempaqueta el dibujo.
 *
 * <p><b>Por qué a mano y no con {@code android-maps-utils}:</b> de esa librería se usaría un único
 * método. Traer con ella el agrupamiento de marcadores, los mapas de calor y el lector de KML para
 * llamar a {@code decode()} no se paga. El formato está publicado y no ha cambiado en más de una
 * década, así que este archivo no es una pieza que haya que mantener al día.
 *
 * <p><b>El formato</b>, en corto: cada coordenada se guarda como su diferencia con la anterior, en
 * millonésimas de grado (precisión 5), y ese entero se parte en grupos de 5 bits que viajan como
 * caracteres ASCII imprimibles. De ahí el ir sumando sobre {@code lat}/{@code lng} en vez de leer
 * valores absolutos.
 */
public final class PolylineDecoder {

    /** Google codifica con precisión 5: los enteros vienen en cienmilésimas de grado. */
    private static final double SCALE = 1e5;

    private PolylineDecoder() {
    }

    /**
     * Los puntos de la ruta, en orden.
     *
     * <p>Devuelve la lista vacía ante cualquier problema —campo ausente, cadena vacía o truncada—
     * en vez de lanzar: quien llama distingue "hay trazo" de "no hay trazo" y cae a la guía recta,
     * que es exactamente el comportamiento que pide el contrato. Un viaje no se cae por un
     * problema de pintura.
     */
    @NonNull
    public static List<LatLng> decode(@Nullable String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Collections.emptyList();
        }

        List<LatLng> points = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;

        while (index < encoded.length()) {
            long deltaLat = readValue(encoded, index);
            if (deltaLat == TRUNCATED) {
                // Cadena cortada a media coordenada: se devuelve lo que se alcanzó a leer, que ya
                // es una ruta parcial dibujable, en vez de descartarlo todo.
                break;
            }
            index += length(deltaLat);
            lat += value(deltaLat);

            long deltaLng = readValue(encoded, index);
            if (deltaLng == TRUNCATED) {
                break;
            }
            index += length(deltaLng);
            lng += value(deltaLng);

            points.add(new LatLng(lat / SCALE, lng / SCALE));
        }
        return points;
    }

    /**
     * Marca de cadena truncada. No colisiona con ningún resultado real: el valor y el número de
     * caracteres leídos se empaquetan en los bits bajos, y este deja el bit 63 encendido.
     */
    private static final long TRUNCATED = Long.MIN_VALUE;

    /**
     * Lee un entero desde {@code index} y devuelve el valor ya decodificado junto con cuántos
     * caracteres ocupó, empaquetados en un {@code long} para poder devolver los dos de una sola
     * pasada sin crear un objeto por coordenada — una ruta urbana trae cientos.
     */
    private static long readValue(String encoded, int index) {
        int result = 0;
        int shift = 0;
        int consumed = 0;
        int chunk;

        do {
            if (index + consumed >= encoded.length() || shift > 30) {
                return TRUNCATED;
            }
            chunk = encoded.charAt(index + consumed) - 63;
            result |= (chunk & 0x1f) << shift;
            shift += 5;
            consumed++;
        } while (chunk >= 0x20); // El bit 6 encendido anuncia que el entero sigue en el siguiente carácter.

        // El bit menos significativo lleva el signo (complemento a uno), no magnitud.
        int decoded = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return ((long) consumed << 32) | (decoded & 0xffffffffL);
    }

    private static int value(long packed) {
        return (int) packed;
    }

    private static int length(long packed) {
        return (int) (packed >> 32);
    }
}
