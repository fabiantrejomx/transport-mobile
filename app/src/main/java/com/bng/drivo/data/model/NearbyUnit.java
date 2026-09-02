package com.bng.drivo.data.model;

/**
 * Unidad disponible cerca del pasajero, para pintar el mapa de inicio — ver
 * {@code GET /nearby-drivers}.
 *
 * <p>Anónima por diseño: el contrato no manda id, nombre, placa ni calificación, así que dos
 * respuestas seguidas no permiten saber si "la unidad de arriba" es la misma de hace 15 s. La
 * posición viene redondeada a una celda de 150 m y puede traer hasta minuto y medio de antigüedad.
 */
public class NearbyUnit {

    private final double lat;
    private final double lng;
    private final Integer etaMin;

    public NearbyUnit(double lat, double lng, Integer etaMin) {
        this.lat = lat;
        this.lng = lng;
        this.etaMin = etaMin;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    /**
     * Minutos aproximados en línea recta. El contrato es explícito en que es una pista visual y
     * <b>nunca</b> una promesa de llegada —lo único que se compromete es la oferta aceptada—, por
     * eso el mapa no lo pinta hoy: una etiqueta de "4 min" sobre un coche anónimo se lee como un
     * compromiso. Se conserva aquí porque es parte de la respuesta, no porque haya UI que lo use.
     */
    public Integer getEtaMin() {
        return etaMin;
    }
}
