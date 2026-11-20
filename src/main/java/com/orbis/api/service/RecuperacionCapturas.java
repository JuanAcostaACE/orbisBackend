package com.orbis.api.service;

import com.orbis.api.model.CapturaFotografia;
import com.orbis.api.model.EstadoCaptura;
import com.orbis.api.repository.CapturaFotografiaRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recuperación tras reinicio (decisión D-003).
 *
 * El procesamiento vive en memoria del proceso. Si el proceso se reinicia con capturas en
 * EN_PROCESO, nada las va a completar. Se marcan como FALLIDA con motivo explícito en lugar
 * de quedar colgadas. Una captura fallida se puede volver a enviar desde el bastón.
 *
 * Nota: no se reanuda el análisis, porque la imagen no se conserva en esta fase
 * (ver ORBIS_NEXT_STEPS.md, decisión pendiente de almacenamiento).
 */
@Component
public class RecuperacionCapturas {

    public static final String MOTIVO_REINICIO = "REINICIO_DURANTE_PROCESO";

    private final CapturaFotografiaRepository repository;
    private final CapturaService capturaService;

    public RecuperacionCapturas(CapturaFotografiaRepository repository, CapturaService capturaService) {
        this.repository = repository;
        this.capturaService = capturaService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        recuperar();
    }

    /** Devuelve cuántas capturas se marcaron como fallidas. */
    public int recuperar() {
        List<CapturaFotografia> atascadas = repository.findByEstado(EstadoCaptura.EN_PROCESO);
        for (CapturaFotografia c : atascadas) {
            capturaService.transicionar(c.getId(), EstadoCaptura.FALLIDA, MOTIVO_REINICIO);
        }
        return atascadas.size();
    }
}
