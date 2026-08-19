package com.bng.drivo.data.model;

/** Una fila de GET /rides — nunca trae al conductor, solo GET /rides/{id} (ver {@link Ride}). */
public class RideSummary {

    private final String id;
    private final String status;
    private final Double agreedFare;
    private final String originText;
    private final String destText;
    private final String requestedAt;

    public RideSummary(String id, String status, Double agreedFare, String originText, String destText,
                        String requestedAt) {
        this.id = id;
        this.status = status;
        this.agreedFare = agreedFare;
        this.originText = originText;
        this.destText = destText;
        this.requestedAt = requestedAt;
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
}
