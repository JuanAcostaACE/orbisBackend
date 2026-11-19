package com.orbis.api.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Borrado periódico de los bytes de capturas caducadas (más de app.analisis.retencion-horas, 24 h por defecto).
 * El intervalo de limpieza (app.analisis.limpieza-ms, 5 min por defecto) determina cuánto puede
 * sobrevivir una imagen por encima del límite; el worker no la usa nunca pasadas las 24 h.
 */
@Component
public class RetencionImagenes {

    private final CapturaService capturaService;

    public RetencionImagenes(CapturaService capturaService) {
        this.capturaService = capturaService;
    }

    @Scheduled(
            initialDelayString = "${app.analisis.limpieza-ms:300000}",
            fixedDelayString = "${app.analisis.limpieza-ms:300000}")
    public void limpiar() {
        capturaService.caducarImagenesAntiguas();
    }
}
