package com.bng.drivo.data.remote.dto;

/**
 * Cuerpo de POST /driver/rides/{id}/complete: dónde está el conductor al cerrar, y —solo cuando el
 * viaje termina antes de llegar al destino— por qué.
 *
 * <p>Tiene forma propia y no reutiliza {@link DriverAtLocationRequest} aunque casi coincidan:
 * marcar llegada y cerrar dejaron de tener las mismas reglas, y compartir la clase haría que el
 * motivo apareciera también donde no significa nada.
 */
public class CompleteRideRequest {

    /**
     * Por qué el viaje terminó antes del destino.
     *
     * <p>Va anidado y no como dos campos sueltos porque los dos van juntos o no va ninguno: su
     * ausencia es exactamente lo que le dice al servidor que este es un cierre normal, con la
     * verificación por GPS de siempre.
     */
    public static class EarlyEnd {
        /** Uno de los valores de {@code EarlyEndReason} del servidor. */
        public String reason;
        /** Texto libre para quien revise un reclamo. No se muestra en ninguna pantalla. */
        public String note;

        public EarlyEnd(String reason, String note) {
            this.reason = reason;
            this.note = note;
        }
    }

    public double lat;
    public double lng;
    /** Null en un cierre normal, que es la inmensa mayoría. */
    public EarlyEnd early_end;

    public CompleteRideRequest(double lat, double lng, EarlyEnd earlyEnd) {
        this.lat = lat;
        this.lng = lng;
        this.early_end = earlyEnd;
    }
}
