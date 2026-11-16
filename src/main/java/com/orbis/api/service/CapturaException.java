package com.orbis.api.service;

import org.springframework.http.HttpStatus;

/** Error de validación o de idempotencia de una captura, con código HTTP y código de error estable. */
public class CapturaException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public CapturaException(HttpStatus status, String codigo) {
        super(codigo);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
