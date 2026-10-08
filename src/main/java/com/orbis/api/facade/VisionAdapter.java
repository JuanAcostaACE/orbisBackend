package com.orbis.api.facade;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Patron Adapter - Hugging Face Image Classification (gratuito)
 * Modelo: microsoft/resnet-50 — rapido, siempre activo en HF free tier
 * Variable requerida en Railway: HUGGINGFACE_TOKEN = hf_xxx...
 */
@Component
public class VisionAdapter implements VisionFacade {

    private static final String HF_URL =
        "https://api-inference.huggingface.co/models/microsoft/resnet-50";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public List<String> analizarImagen(String imagenBase64) {
        if (imagenBase64 == null || imagenBase64.isBlank()) {
            throw new VisionException("La imagen no puede estar vacia");
        }

        String token = System.getenv("HUGGINGFACE_TOKEN");
        if (token == null || token.isBlank()) {
            throw new VisionException("Variable HUGGINGFACE_TOKEN no configurada en Railway");
        }

        // Limpiar prefijo data:image/...;base64,
        String base64Limpio = imagenBase64.contains(",")
                ? imagenBase64.split(",")[1]
                : imagenBase64;

        byte[] imagenBytes;
        try {
            imagenBytes = Base64.getDecoder().decode(base64Limpio.trim());
        } catch (IllegalArgumentException e) {
            throw new VisionException("Imagen Base64 invalida: " + e.getClass().getSimpleName());
        }

        try {
            HttpResponse<String> response = llamarAPI(token.trim(), imagenBytes);

            // Retry si modelo esta cargando (503 cold start)
            if (response.statusCode() == 503) {
                Thread.sleep(5000);
                response = llamarAPI(token.trim(), imagenBytes);
            }

            if (response.statusCode() == 401) {
                throw new VisionException("Token de Hugging Face invalido (HTTP 401) - verifique HUGGINGFACE_TOKEN");
            }
            if (response.statusCode() != 200) {
                String body = response.body() != null
                    ? response.body().substring(0, Math.min(150, response.body().length()))
                    : "sin respuesta";
                throw new VisionException("Hugging Face HTTP " + response.statusCode() + ": " + body);
            }

            List<String> etiquetas = extraerEtiquetas(response.body());
            return etiquetas.isEmpty() ? List.of("Objeto detectado") : etiquetas;

        } catch (VisionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisionException("Llamada a Hugging Face interrumpida");
        } catch (Exception e) {
            String tipo = e.getClass().getSimpleName();
            String msg  = e.getMessage() != null ? e.getMessage() : "sin detalle";
            throw new VisionException("Error " + tipo + ": " + msg);
        }
    }

    private HttpResponse<String> llamarAPI(String token, byte[] bytes) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HF_URL))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .timeout(Duration.ofSeconds(30))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Extrae etiquetas del JSON de resnet-50:
     * [{"score":0.99,"label":"tabby cat"},{"score":0.8,"label":"tiger cat"},...]
     */
    private List<String> extraerEtiquetas(String json) {
        List<String> result = new java.util.ArrayList<>();
        Pattern p = Pattern.compile("\"label\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        int count = 0;
        while (m.find() && count < 3) {
            String label = m.group(1);
            // Limpiar labels compuestas tipo "tabby, tabby cat" -> "tabby cat"
            if (label.contains(",")) {
                label = label.substring(label.lastIndexOf(",") + 1).trim();
            }
            result.add(capitalize(label));
            count++;
        }
        return result;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}