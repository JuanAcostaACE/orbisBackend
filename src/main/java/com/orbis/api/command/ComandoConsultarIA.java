package com.orbis.api.command;

import com.orbis.api.facade.VisionFacade;

import java.util.List;

/**
 * Patrón Command — Comando concreto: Consultar IA.
 * Encapsula la llamada a Google Cloud Vision API.
 * ActivoStrategy lo crea y delega al EjecutorComando,
 * sin invocar la API directamente desde la strategy.
 */
public class ComandoConsultarIA implements Comando<List<String>> {

    private final VisionFacade visionFacade;
    private final String imagenBase64;

    public ComandoConsultarIA(VisionFacade visionFacade, String imagenBase64) {
        this.visionFacade = visionFacade;
        this.imagenBase64 = imagenBase64;
    }

    @Override
    public List<String> ejecutar() {
        return visionFacade.analizarImagen(imagenBase64);
    }
}
