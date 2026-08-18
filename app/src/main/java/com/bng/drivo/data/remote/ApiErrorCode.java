package com.bng.drivo.data.remote;

/**
 * Catálogo completo de {@code code} que puede devolver transport-api (ver openapi.yaml).
 * Los repositorios ramifican siempre por este enum, nunca por el HTTP status ni el texto
 * de {@code message} (el contrato es explícito: el mensaje puede cambiar de redacción).
 */
public enum ApiErrorCode {
    UNAUTHENTICATED,
    VALIDATION_ERROR,
    MALFORMED_REQUEST,
    INVALID_IDEMPOTENCY_KEY,
    INVALID_ROLE,
    IDEMPOTENCY_KEY_REUSED,
    REQUEST_IN_PROGRESS,
    QUOTE_EXPIRED,
    OFFER_OUT_OF_RANGE,
    RIDE_IN_PROGRESS,
    ALREADY_OFFERED,
    RIDE_ALREADY_TAKEN,
    OFFER_EXPIRED,
    DRIVER_NO_LONGER_AVAILABLE,
    RIDE_NOT_FOUND,
    INVALID_STATE_TRANSITION,
    TOO_FAR_FROM_PICKUP,
    DRIVER_NOT_APPROVED,
    NO_APPLICATION,
    INSUFFICIENT_BALANCE,
    DRIVER_OFFLINE,
    TOO_MANY_PINGS,
    INVALID_MODALITY,
    DOCUMENT_NOT_REQUIRED,
    FORBIDDEN_PATH,
    FAVORITE_NOT_FOUND,
    ROUTING_UNAVAILABLE,
    /** No hay respuesta del servidor (sin red, timeout, DNS, etc.) — no es un código del contrato. */
    NETWORK_ERROR,
    /** Código nuevo del servidor que este cliente todavía no conoce, o cuerpo de error no parseable. */
    UNKNOWN
}
