package com.orbis.api;

import com.orbis.api.service.CapturaService;
import com.orbis.api.service.ValidadorJpeg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flujo HTTP de capturas fotográficas con token de dispositivo.
 * Token y propietario existen solo para esta clase de prueba.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:captura-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.device.token=token-captura-no-real-0123456789",
                "app.device.ownerEmail=captura-dueno@test.com"
        })
@AutoConfigureMockMvc
class CapturaHttpTest {

    static final String TOKEN = "token-captura-no-real-0123456789";
    static final String ID_1 = "0b7e5f2a-41c9-4d8e-b3a6-9f1e2c7d4a85";
    static final String ID_2 = "1c8f6a3b-52da-4e9f-84b7-0a2f3d8e5b96";

    @Autowired MockMvc mvc;
    @Autowired CapturaService capturaService;

    static byte[] jpeg(int tamano, byte relleno) {
        return CapturaValidacionTest.jpeg(tamano, relleno);
    }

    private org.springframework.test.web.servlet.ResultActions subir(String id, byte[] cuerpo, String tipo)
            throws Exception {
        return mvc.perform(post("/api/v1/dispositivos/capturas")
                .contentType(tipo)
                .header("X-Device-Token", TOKEN)
                .header("X-Capture-Id", id)
                .content(cuerpo));
    }

    @Test
    @DisplayName("Captura nueva válida → 202 y estado RECIBIDA")
    void captura_nueva_202() throws Exception {
        subir(ID_1, jpeg(20 * 1024, (byte) 0x01), "image/jpeg")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.captureId").value(ID_1))
                .andExpect(jsonPath("$.estado").value("RECIBIDA"))
                .andExpect(jsonPath("$.duplicada").value(false));
    }

    @Test
    @DisplayName("Reintento idéntico → 200 con duplicada=true, sin evento nuevo")
    void reintento_identico_200() throws Exception {
        byte[] foto = jpeg(30 * 1024, (byte) 0x02);
        subir(ID_2, foto, "image/jpeg").andExpect(status().isAccepted());
        subir(ID_2, foto, "image/jpeg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicada").value(true))
                .andExpect(jsonPath("$.captureId").value(ID_2));
        subir(ID_2, foto, "image/jpeg").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mismo X-Capture-Id con contenido distinto → 409 CAPTURA_CONFLICTO")
    void mismo_id_contenido_distinto_409() throws Exception {
        String id = "2d9a7b4c-63eb-4fa0-95c8-1b3e4f9a6ca7";
        subir(id, jpeg(20 * 1024, (byte) 0x03), "image/jpeg").andExpect(status().isAccepted());
        subir(id, jpeg(20 * 1024, (byte) 0x04), "image/jpeg")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CAPTURA_CONFLICTO"));
    }

    @Test
    @DisplayName("Tipo distinto de JPEG → 415")
    void tipo_415() throws Exception {
        subir("3e0b8c5d-74fc-4b1a-86d9-2c4f5a0b7db8", jpeg(20 * 1024, (byte) 0x05), "image/png")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("TIPO_NO_SOPORTADO"));
    }

    @Test
    @DisplayName("JPEG sin marcadores válidos → 422")
    void jpeg_invalido_422() throws Exception {
        byte[] basura = jpeg(20 * 1024, (byte) 0x06);
        basura[0] = 0;
        subir("4f1c9d6e-85ad-4c2b-97ea-3d5a6b1c8ec9", basura, "image/jpeg")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("IMAGEN_NO_VALIDA"));
    }

    @Test
    @DisplayName("Imagen por encima de 512 KB → 413 sin cargarla entera")
    void demasiado_grande_413() throws Exception {
        subir("50ad2e7f-96be-4d3c-a8fb-4e6b7c2d9fd0",
                jpeg(ValidadorJpeg.TAMANO_MAXIMO_BYTES + 10, (byte) 0x07), "image/jpeg")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("IMAGEN_DEMASIADO_GRANDE"));
    }

    @Test
    @DisplayName("X-Capture-Id ausente o no UUID → 400")
    void id_invalido_400() throws Exception {
        mvc.perform(post("/api/v1/dispositivos/capturas")
                        .contentType("image/jpeg")
                        .header("X-Device-Token", TOKEN)
                        .content(jpeg(20 * 1024, (byte) 0x08)))
                .andExpect(status().isBadRequest());
        subir("no-es-un-uuid", jpeg(20 * 1024, (byte) 0x09), "image/jpeg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ID_CAPTURA_INVALIDO"));
    }

    @Test
    @DisplayName("Sin token ni sesión → 401")
    void sin_token_401() throws Exception {
        mvc.perform(post("/api/v1/dispositivos/capturas")
                        .contentType("image/jpeg")
                        .header("X-Capture-Id", "60be3f8a-a7cf-4e4d-b9ac-5f7c8d3e0fe1")
                        .content(jpeg(20 * 1024, (byte) 0x0A)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Consulta de estado propio → 200; ID inexistente → 404")
    void estado_propio_y_404() throws Exception {
        String id = "71cf4a9b-b8d0-4e5e-8cbd-6a8d9e4f1af2";
        subir(id, jpeg(20 * 1024, (byte) 0x0B), "image/jpeg").andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/dispositivos/capturas/" + id).header("X-Device-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECIBIDA"));
        mvc.perform(get("/api/v1/dispositivos/capturas/82d05b0c-c9e1-4f6f-8dce-7b9e0f5a2b03")
                        .header("X-Device-Token", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Una captura de un propietario no es visible para otro")
    void aislamiento_entre_propietarios() throws Exception {
        String id = "93e16c1d-d0f2-4a7a-9ed9-8c0f1a6b3c14";
        subir(id, jpeg(20 * 1024, (byte) 0x0C), "image/jpeg").andExpect(status().isAccepted());
        assertTrue(capturaService.obtener("captura-dueno@test.com", id).isPresent());
        assertTrue(capturaService.obtener("otro-usuario@test.com", id).isEmpty());
    }
}
