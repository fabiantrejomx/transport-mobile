package com.bng.drivo.data.remote;

/** Forma exacta de todo error de transport-api: {@code {"code": "...", "message": "..."}}. */
class ApiErrorBody {
    String code;
    String message;
}
