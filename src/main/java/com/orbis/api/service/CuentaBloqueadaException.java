package com.orbis.api.service;

/**
 * La cuenta supera el límite de intentos fallidos de login. Se traduce a 429 con el mismo
 * cuerpo JSON que el resto de límites, sin indicar si el correo existe.
 */
public class CuentaBloqueadaException extends RuntimeException {

    private final long segundosRestantes;

    public CuentaBloqueadaException(long segundosRestantes) {
        super("Demasiados intentos. Intenta de nuevo más tarde");
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }
}
