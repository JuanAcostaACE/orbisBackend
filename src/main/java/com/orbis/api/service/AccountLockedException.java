package com.orbis.api.service;

/**
 * La cuenta supera el límite de intentos fallidos de login. Se traduce a 429 con el mismo
 * cuerpo JSON que el resto de límites, sin indicar si el correo existe.
 */
public class AccountLockedException extends RuntimeException {

    private final long remainingSeconds;

    public AccountLockedException(long remainingSeconds) {
        super("Demasiados intentos. Intenta de nuevo más tarde");
        this.remainingSeconds = remainingSeconds;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }
}
