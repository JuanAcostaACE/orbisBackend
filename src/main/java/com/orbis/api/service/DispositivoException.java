package com.orbis.api.service;

import org.springframework.http.HttpStatus;

/** Error de registro, autenticación o gestión de dispositivos, con código de error estable. */
public class DispositivoException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public DispositivoException(HttpStatus status, String codigo) {
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
