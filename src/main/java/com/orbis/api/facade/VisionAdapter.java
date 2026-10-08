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
 * Patron Adapter — Hugging Face BLIP Image Captioning
 *
 * Modelo: Salesforce/blip-image-captioning-base
 * - Gratuito, sin limite estricto para uso academico
 * - Genera descripciones en lenguaje natural: "a chair next to a wall"
 * - Ideal para tecnologia asistiva (descripcion verbal de obstaculos)
 *
 * Configuracion en Railway:
 *   HUGGINGFACE_TOKEN = hf_xxxxxxxxxxxxxxxxxx
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

        try {
            byte[] imagenBytes = Base64.getDecoder().decode(base64Limpio);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HF_URL))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(imagenBytes))
                    .timeout(Duration.ofSeconds(45))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 503) {
                // Modelo cargando (cold start de Hugging Face), reintentar
                Thread.sleep(3000);
                response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() != 200) {
                throw new VisionException("Hugging Face respondio con error " + response.statusCode()
                        + ": " + response.body());
            }

            String descripcion = extraerTexto(response.body());
            return List.of(descripcion);

        } catch (VisionException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new VisionException("Imagen Base64 invalida", e);
        } catch (Exception e) {
            throw new VisionException("Error al conectar con Hugging Face: " + e.getMessage(), e);
        }
    }

    /**
     * Extrae el texto generado del JSON de respuesta.
     * Formato esperado: [{"generated_text":"a person sitting on a chair"}]
     */
    private String extraerTexto(String json) {
        Pattern p = Pattern.compile("\"generated_text\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return capitalizar(m.group(1));
        }
        // Fallback: devolver el JSON completo si no hay match
        return json.replaceAll("[\\[\\]{}\"]", "").trim();
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isBlank()) return texto;
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}