package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.RegistroRequest;
import com.orbis.api.model.Usuario;
import com.orbis.api.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registro anti-enumeración: el mismo status y el mismo cuerpo exista o no el correo,
 * una sola fila por correo y 400 para datos inválidos.
 * BD H2 propia y límites de rate limit altos para no interferir con otras pruebas.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:registro-anonimato;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.ratelimit.registro.max-intentos=1000"
        })
@AutoConfigureMockMvc
class RegistroAnonimatoTest {

    static final String ESPERADO =
            "{\"status\":201,\"mensaje\":\"Solicitud procesada. Inicia sesión para continuar.\"}";
    static final String PASS = "contraseña_segura_123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepo;
    final ObjectMapper mapper = new ObjectMapper();

    private MvcResult registrar(String nombre, String correo, String contrasena) throws Exception {
        return mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegistroRequest(nombre, correo, contrasena))))
                .andReturn();
    }

    private long filasPara(String correo) {
        List<Usuario> todos = usuarioRepo.findAll();
        return todos.stream().filter(u -> u.getCorreo().equals(correo)).count();
    }

    @Test
    @DisplayName("Correo nuevo y correo ya registrado: mismo status 201 y mismo cuerpo exacto")
    void mismo_status_y_cuerpo_nuevo_y_duplicado() throws Exception {
        String correo = "nuevo-anon@test.com";

        MvcResult nuevo = registrar("Nuevo", correo, PASS);
        MvcResult duplicado = registrar("Otro nombre", correo, PASS);

        assertThat(nuevo.getResponse().getStatus()).isEqualTo(201);
        assertThat(duplicado.getResponse().getStatus()).isEqualTo(201);
        assertThat(nuevo.getResponse().getContentAsString()).isEqualTo(ESPERADO);
        assertThat(duplicado.getResponse().getContentAsString()).isEqualTo(ESPERADO);
    }

    @Test
    @DisplayName("Registro duplicado no crea una segunda fila para el correo")
    void sin_fila_duplicada() throws Exception {
        String correo = "unica-fila@test.com";
        registrar("Primero", correo, PASS);
        registrar("Segundo", correo, PASS);
        registrar("Tercero", correo, PASS);

        assertThat(filasPara(correo)).isEqualTo(1);
    }

    @Test
    @DisplayName("El correo se normaliza: mayúsculas y duplicado comparten fila y respuesta")
    void correo_normalizado() throws Exception {
        MvcResult a = registrar("Mayus", "MAYUS-anon@Test.com", PASS);
        MvcResult b = registrar("Minus", "mayus-anon@test.com", PASS);

        assertThat(a.getResponse().getContentAsString()).isEqualTo(ESPERADO);
        assertThat(b.getResponse().getContentAsString()).isEqualTo(ESPERADO);
        assertThat(filasPara("mayus-anon@test.com")).isEqualTo(1);
    }

    @Test
    @DisplayName("Datos inválidos siguen devolviendo 400, también con un correo ya registrado")
    void invalidos_400_aunque_exista() throws Exception {
        String existente = "invalido-anon@test.com";
        registrar("Existe", existente, PASS);

        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegistroRequest("X", "no-es-correo", PASS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegistroRequest("X", existente, "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
