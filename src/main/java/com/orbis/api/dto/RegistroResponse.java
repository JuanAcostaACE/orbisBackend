package com.orbis.api.dto;

/**
 * Respuesta uniforme de POST /auth/registro.
 * Es idéntica para un correo nuevo y para uno ya registrado, para no revelar
 * si una cuenta existe (anti-enumeración). No confirma envío de ningún correo.
 */
public record RegistroResponse(
        int    status,
        String mensaje
) {}
