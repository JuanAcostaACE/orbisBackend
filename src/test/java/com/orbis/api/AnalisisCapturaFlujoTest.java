package com.orbis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.repository.CapturaImagenRepository;
import com.orbis.api.service.CapturaService;
import com.orbis.api.vision.yolo.DescriptorEscena;
import com.orbis.api.vision.yolo.DetectorYoloxRealTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flujo completo: POST de captura -> worker real (YOLOX) -> GET con estado final.
 * Usa base H2 propia y el worker activo.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:analisis-flujo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.device.token=token-analisis-no-real-0123456789",
                "app.device.ownerEmail=analisis-dueno@test.com",
                "app.analisis.activo=true",
                "app.analisis.intervalo-ms=200",
                "app.analisis.retardo-inicial-ms=200"
        })
@AutoConfigureMockMvc
class AnalisisCapturaFlujoTest {

    static final String TOKEN = "token-analisis-no-real-0123456789";
    static final long TIMEOUT_MS = 20_000;

    @Autowired MockMvc mvc;
    @Autowired CapturaService capturaService;
    @Autowired CapturaImagenRepository imagenes;
    final ObjectMapper json = new ObjectMapper();

    private ResultActions subir(String id, byte[] cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/dispositivos/capturas")
                .contentType("image/jpeg")
                .header("X-Device-Token", TOKEN)
                .header("X-Capture-Id", id)
                .content(cuerpo));
    }

    /** Espera (poll) hasta un estado final o hasta TIMEOUT_MS. Devuelve el JSON del último GET. */
    private JsonNode esperarFinal(String id) throws Exception {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        JsonNode ultimo = null;
        while (System.currentTimeMillis() < limite) {
            String cuerpo = mvc.perform(get("/api/v1/dispositivos/capturas/" + id)
                            .header("X-Device-Token", TOKEN))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ultimo = json.readTree(cuerpo);
            String estado = ultimo.get("estado").asText();
            if (estado.equals("DESCRITA") || estado.equals("FALLIDA")) {
                return ultimo;
            }
            Thread.sleep(100);
        }
        fail("La captura " + id + " no llegó a estado final en " + TIMEOUT_MS + " ms; último: " + ultimo);
        return null;
    }

    /** JPEG real decodificable: rectángulo rojo y ruido determinista, con cabecera y fin válidos. */
    static byte[] jpegReal() throws IOException {
        return DetectorYoloxRealTest.comoJpeg(DetectorYoloxRealTest.imagenDePrueba(320, 240));
    }

    /** Bytes con marcadores JPEG válidos pero contenido no decodificable. */
    static byte[] jpegCorrupto() {
        byte[] b = new byte[6000];
        new Random(7).nextBytes(b);
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[b.length - 2] = (byte) 0xFF;
        b[b.length - 1] = (byte) 0xD9;
        return b;
    }

    @Test
    @DisplayName("(c) Captura válida -> worker -> DESCRITA con descripción en español")
    void flujo_completo_descrita() throws Exception {
        String id = "a0c1d2e3-f405-4a6b-8c7d-9e0f1a2b3c4d";
        subir(id, jpegReal()).andExpect(status().isAccepted());

        JsonNode estado = esperarFinal(id);
        assertEquals("DESCRITA", estado.get("estado").asText(), estado.toString());
        String descripcion = estado.get("descripcion").asText();
        assertFalse(descripcion.isBlank());
        assertTrue(descripcion.equals(DescriptorEscena.SIN_OBJETOS) || descripcion.startsWith("Hay "),
                "descripción inesperada: " + descripcion);
        assertTrue(estado.get("etiquetas").isArray());
        assertTrue(estado.get("motivoFallo") == null || estado.get("motivoFallo").isNull());
    }

    @Test
    @DisplayName("(d) JPEG corrupto -> FALLIDA con motivo claro, sin excepción no controlada")
    void jpeg_corrupto_fallida() throws Exception {
        String id = "b1d2e3f4-0516-4b7c-9d8e-0f1a2b3c4d5e";
        subir(id, jpegCorrupto()).andExpect(status().isAccepted());

        JsonNode estado = esperarFinal(id);
        assertEquals("FALLIDA", estado.get("estado").asText(), estado.toString());
        assertEquals("IMAGEN_NO_DECODIFICABLE", estado.get("motivoFallo").asText());
        assertTrue(estado.get("descripcion") == null || estado.get("descripcion").isNull());
    }

    @Test
    @DisplayName("(e) Los bytes se borran al terminar (DESCRITA y FALLIDA)")
    void bytes_borrados_al_terminar() throws Exception {
        String idOk = "c2e3f405-1627-4c8d-8e9f-1a2b3c4d5e6f";
        String idMal = "d3f40516-2738-4d9e-9fa0-2b3c4d5e6f70";
        subir(idOk, jpegReal()).andExpect(status().isAccepted());
        subir(idMal, jpegCorrupto()).andExpect(status().isAccepted());
        esperarFinal(idOk);
        esperarFinal(idMal);

        for (String id : Arrays.asList(idOk, idMal)) {
            Long dbId = capturaService.obtener("analisis-dueno@test.com", id).orElseThrow().getId();
            assertEquals(0, imagenes.countByCaptura_Id(dbId), "bytes aún presentes para " + id);
            assertEquals(0, capturaService.contarImagenes(dbId));
        }
    }
}
