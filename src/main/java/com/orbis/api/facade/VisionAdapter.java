package com.orbis.api.facade;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Patrón Adapter:
 * Adapta la API de Google Cloud Vision al contrato definido por VisionFacade.
 * ActivoStrategy no conoce nada de Google Cloud Vision; solo habla con VisionFacade.
 *
 * REQUISITO: La variable de entorno GOOGLE_APPLICATION_CREDENTIALS debe apuntar
 * al archivo JSON de la Service Account descargado desde Google Cloud Console.
 */
@Component
public class VisionAdapter implements VisionFacade {

    private static final int MAX_RESULTADOS = 10;

    @Override
    public List<String> analizarImagen(String imagenBase64) {
        if (imagenBase64 == null || imagenBase64.isBlank()) {
            throw new VisionException("La imagen no puede estar vacía");
        }

        // Limpia el prefijo data:image/...;base64, si viene del frontend
        String base64Limpio = imagenBase64.contains(",")
                ? imagenBase64.split(",")[1]
                : imagenBase64;

        try (ImageAnnotatorClient cliente = ImageAnnotatorClient.create()) {

            ByteString contenidoImagen = ByteString.copyFrom(Base64.getDecoder().decode(base64Limpio));
            Image imagen = Image.newBuilder().setContent(contenidoImagen).build();

            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.LABEL_DETECTION)
                    .setMaxResults(MAX_RESULTADOS)
                    .build();

            AnnotateImageRequest requestVision = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(imagen)
                    .build();

            BatchAnnotateImagesResponse response = cliente.batchAnnotateImages(
                    List.of(requestVision)
            );

            AnnotateImageResponse imageResponse = response.getResponsesList().get(0);

            if (imageResponse.hasError()) {
                throw new VisionException("Google Vision retornó error: " + imageResponse.getError().getMessage());
            }

            return imageResponse.getLabelAnnotationsList().stream()
                    .map(EntityAnnotation::getDescription)
                    .collect(Collectors.toList());

        } catch (VisionException e) {
            throw e; // Re-lanzar excepciones propias sin envolver
        } catch (IllegalArgumentException e) {
            throw new VisionException("Imagen en formato Base64 inválido", e);
        } catch (Exception e) {
            throw new VisionException("Error al conectar con Google Cloud Vision: " + e.getMessage(), e);
        }
    }
}
