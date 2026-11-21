package com.orbis.api.command;

import com.orbis.api.facade.VisionFacade;

import java.util.List;

/**
 * Patrón Command — Comando concreto: Consultar IA.
 * Encapsula la llamada a Google Cloud Vision API.
 * ActiveStrategy lo crea y delega al CommandExecutor,
 * sin invocar la API directamente desde la strategy.
 */
public class QueryAiCommand implements Command<List<String>> {

    private final VisionFacade visionFacade;
    private final String imageBase64;

    public QueryAiCommand(VisionFacade visionFacade, String imageBase64) {
        this.visionFacade = visionFacade;
        this.imageBase64 = imageBase64;
    }

    @Override
    public List<String> execute() {
        return visionFacade.analyzeImage(imageBase64);
    }
}
