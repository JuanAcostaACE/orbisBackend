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

/**
 * Patron Adapter - Hugging Face BLIP Image Captioning (gratuito)
 * Modelo: Salesforce/blip-image-captioning-base
 * Variable requerida en Railway: HUGGINGFACE_TOKEN = hf_xxx...
 */
@Component
public class VisionAdapter implements VisionFacade {

    private static final String HF_URL =
        "https://api-inference.huggingface.co/models/Salesforce/blip-image-captioning-base";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
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
        } catch (Exception e) {
            throw new VisionException("Imagen Base64 invalida: " + e.getClass().getSimpleName());
        }

        try {
            HttpResponse<String> response = llamarHuggingFace(token, imagenBytes);

            // Retry si modelo esta cargando (cold start 503)
            if (response.statusCode() == 503) {
                Thread.sleep(4000);
                response = llamarHuggingFace(token, imagenBytes);
            }

            if (response.statusCode() != 200) {
                String body = response.body() != null ? response.body().substring(0, Math.min(200, response.body().length())) : "sin body";
                throw new VisionException("Hugging Face HTTP " + response.statusCode() + ": " + body);
            }

            String descripcion = extraerTexto(response.body());
            if (descripcion == null || descripcion.isBlank()) {
                return List.of("Imagen analizada sin etiquetas");
            }
            return List.of(capitalizar(descripcion));

        } catch (VisionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisionException("Llamada a Hugging Face interrumpida");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new VisionException("Error Hugging Face: " + msg);
        }
    }

    private HttpResponse<String> llamarHuggingFace(String token, byte[] bytes) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HF_URL))
                .header("Authorization", "Bearer " + token.trim())
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .timeout(Duration.ofSeconds(60))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String extraerTexto(String json) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"generated_text\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return json.replaceAll("[\\[\\]{}\"]", "").trim();
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isBlank()) return texto;
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}