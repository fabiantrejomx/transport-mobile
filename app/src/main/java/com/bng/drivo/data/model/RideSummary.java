package com.bng.drivo.data.model;

/** Una fila de GET /rides — nunca trae al conductor, solo GET /rides/{id} (ver {@link Ride}). */
public class RideSummary {

    private final String id;
    private final String status;
    private final Double agreedFare;
    private final String originText;
    private final String destText;
    private final String requestedAt;
    /**
     * Las estrellas que este lado ya le puso al viaje, o null si no lo ha calificado. Un
     * {@code COMPLETED} con esto en null es una calificación pendiente — la única forma de saberlo
     * al arrancar, porque la pantalla de recibo no sobrevive a que se cierre la app.
     */
    private final Integer myRating;

    public RideSummary(String id, String status, Double agreedFare, String originText, String destText,
                        String requestedAt, Integer myRating) {
        this.id = id;
        this.status = status;
        this.agreedFare = agreedFare;
        this.originText = originText;
        this.destText = destText;
        this.requestedAt = requestedAt;
        this.myRating = myRating;
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public Double getAgreedFare() {
        return agreedFare;
    }

    public String getOriginText() {
        return originText;
    }

    public String getDestText() {
        return destText;
    }

    public String getRequestedAt() {
        return requestedAt;
    }

    public Integer getMyRating() {
        return myRating;
    }

    /** Terminado y sin calificar: lo que la app tiene que ofrecer al arrancar. */
    public boolean needsRating() {
        return "COMPLETED".equals(status) && myRating == null;
    }
}
