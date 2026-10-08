package com.orbis.api.facade;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Patron Adapter:
 * Adapta la API de Google Cloud Vision al contrato definido por VisionFacade.
 *
 * Credenciales (en orden de prioridad):
 *   1. Variable de entorno GOOGLE_CREDENTIALS_JSON  → contenido JSON directo (Railway/Docker)
 *   2. Variable de entorno GOOGLE_APPLICATION_CREDENTIALS → ruta al archivo JSON (local)
 *
 * Para Railway: copiar el contenido completo del archivo service-account.json
 * en la variable de entorno GOOGLE_CREDENTIALS_JSON del servicio orbisBackend.
 */
@Component
public class VisionAdapter implements VisionFacade {

    private static final int MAX_RESULTADOS = 10;

    @Override
    public List<String> analizarImagen(String imagenBase64) {
        if (imagenBase64 == null || imagenBase64.isBlank()) {
            throw new VisionException("La imagen no puede estar vacia");
        }

        String base64Limpio = imagenBase64.contains(",")
                ? imagenBase64.split(",")[1]
                : imagenBase64;

        try (ImageAnnotatorClient cliente = crearCliente()) {

            ByteString contenido = ByteString.copyFrom(Base64.getDecoder().decode(base64Limpio));
            Image imagen = Image.newBuilder().setContent(contenido).build();

            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.LABEL_DETECTION)
                    .setMaxResults(MAX_RESULTADOS)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(imagen)
                    .build();

            BatchAnnotateImagesResponse response = cliente.batchAnnotateImages(List.of(request));
            AnnotateImageResponse imageResponse = response.getResponsesList().get(0);

            if (imageResponse.hasError()) {
                throw new VisionException("Google Vision error: " + imageResponse.getError().getMessage());
            }

            return imageResponse.getLabelAnnotationsList().stream()
                    .map(EntityAnnotation::getDescription)
                    .collect(Collectors.toList());

        } catch (VisionException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new VisionException("Imagen Base64 invalida", e);
        } catch (Exception e) {
            throw new VisionException("Error al conectar con Google Cloud Vision: " + e.getMessage(), e);
        }
    }

    /**
     * Crea el cliente de Vision con credenciales desde env var JSON (Railway)
     * o desde el archivo en GOOGLE_APPLICATION_CREDENTIALS (local).
     */
    private ImageAnnotatorClient crearCliente() throws Exception {
        String credJson = System.getenv("GOOGLE_CREDENTIALS_JSON");

        if (credJson != null && !credJson.isBlank()) {
            // Railway: credenciales como contenido JSON en variable de entorno
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(credJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");

            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            return ImageAnnotatorClient.create(settings);
        }

        // Local: usa GOOGLE_APPLICATION_CREDENTIALS (archivo JSON)
        return ImageAnnotatorClient.create();
    }
}