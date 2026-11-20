package com.orbis.api;

import com.orbis.api.model.CapturaFotografia;
import com.orbis.api.repository.CapturaFotografiaRepository;
import com.orbis.api.service.CapturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Listado paginado de capturas del propietario. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:lista-capturas;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.device.token=token-lista-no-real-0123456789",
                "app.device.ownerEmail=lista-dueno@test.com",
                "app.analisis.activo=false"
        })
@AutoConfigureMockMvc
class ListaCapturasTest {

    static final String RUTA = "/api/v1/dispositivos/capturas";
    static final String TOKEN = "token-lista-no-real-0123456789";
    static final String DUENO = "lista-dueno@test.com";
    static final String OTRO = "lista-otro@test.com";

    @Autowired MockMvc mvc;
    @Autowired CapturaService capturaService;
    @Autowired CapturaFotografiaRepository repo;
    @Autowired com.orbis.api.repository.CapturaImagenRepository imagenes;

    @BeforeEach
    void limpiar() {
        imagenes.deleteAll();
        repo.deleteAll();
    }

    /** Crea una captura y fija recibidaEn para que el orden sea determinista. */
    private CapturaFotografia crear(String correo, int n, LocalDateTime recibida, String etiquetas) {
        String id = UUID.randomUUID().toString();
        CapturaFotografia c = capturaService.recibir(correo, id,
                CapturaValidacionTest.jpeg(8 * 1024,(byte) (n + 1))).captura();
        c = repo.findById(c.getId()).orElseThrow();
        c.setRecibidaEn(recibida);
        c.setEtiquetas(etiquetas);
        return repo.saveAndFlush(c);
    }

    private ResultActions listar(String query) throws Exception {
        return mvc.perform(get(RUTA + query).header("X-Device-Token", TOKEN));
    }

    @Test
    @DisplayName("Solo devuelve las capturas del propietario autenticado")
    void aislamiento() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        crear(DUENO, 1, t, null);
        crear(DUENO, 2, t.plusMinutes(1), null);
        crear(OTRO, 3, t.plusMinutes(2), null);
        listar("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(2));
        mvc.perform(get(RUTA).with(user(OTRO).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("Orden descendente por recibidaEn")
    void orden() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        CapturaFotografia a = crear(DUENO, 1, t, null);
        CapturaFotografia b = crear(DUENO, 2, t.plusHours(2), null);
        CapturaFotografia c = crear(DUENO, 3, t.plusHours(1), null);
        listar("").andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].captureId").value(b.getCaptureId()))
                .andExpect(jsonPath("$.contenido[1].captureId").value(c.getCaptureId()))
                .andExpect(jsonPath("$.contenido[2].captureId").value(a.getCaptureId()));
    }

    @Test
    @DisplayName("Paginación con page y size")
    void paginacion() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        for (int i = 0; i < 5; i++) {
            crear(DUENO, i, t.plusMinutes(i), null);
        }
        listar("?page=1&size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.paginaActual").value(1))
                .andExpect(jsonPath("$.tamanioPagina").value(2))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.primera").value(false))
                .andExpect(jsonPath("$.ultima").value(false));
        listar("?page=2&size=2")
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.ultima").value(true));
        listar("?page=-3&size=2")
                .andExpect(jsonPath("$.paginaActual").value(0));
    }

    @Test
    @DisplayName("size mayor que 50 se recorta a 50; por defecto 20")
    void size_maximo() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        for (int i = 0; i < 3; i++) {
            crear(DUENO, i, t.plusMinutes(i), null);
        }
        listar("?size=500").andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanioPagina").value(50));
        listar("").andExpect(jsonPath("$.tamanioPagina").value(20));
    }

    @Test
    @DisplayName("Sin autenticación → 401")
    void sin_auth() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Etiquetas 'persona,coche' → lista; null → []")
    void etiquetas() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        crear(DUENO, 1, t, null);
        crear(DUENO, 2, t.plusMinutes(1), "persona,coche");
        listar("").andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].etiquetas[0]").value("persona"))
                .andExpect(jsonPath("$.contenido[0].etiquetas[1]").value("coche"))
                .andExpect(jsonPath("$.contenido[0].etiquetas.length()").value(2))
                .andExpect(jsonPath("$.contenido[1].etiquetas.length()").value(0));
    }

    @Test
    @DisplayName("El JSON no expone correo, sha256 ni tamaño")
    void sin_datos_sensibles() throws Exception {
        CapturaFotografia c = crear(DUENO, 1, LocalDateTime.of(2026, 1, 1, 10, 0), null);
        String json = listar("").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(json.contains(DUENO), json);
        assertFalse(json.contains(c.getSha256()), json);
        assertFalse(json.contains("sha256"), json);
        assertFalse(json.contains("tamano"), json);
        assertFalse(json.contains("propietario"), json);
        System.out.println("JSON_EJEMPLO=" + json);
    }
}
