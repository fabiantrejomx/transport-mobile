package com.bng.drivo.data.remote.dto;

/** Cuerpo compartido por accept-offer y reject-offer: ambos solo piden {@code offer_id}. */
public class OfferIdRequest {
    public String offer_id;

    public OfferIdRequest(String offerId) {
        this.offer_id = offerId;
    }
}
