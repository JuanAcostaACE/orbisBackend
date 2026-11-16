package com.orbis.api;

import com.orbis.api.model.CodigoVinculacion;
import com.orbis.api.model.Dispositivo;
import com.orbis.api.repository.CodigoVinculacionRepository;
import com.orbis.api.repository.DispositivoRepository;
import com.orbis.api.service.DispositivoException;
import com.orbis.api.service.DispositivoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Identidad por dispositivo (D-002): vinculación, registro, autenticación, rotación y revocación. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dispositivo-servicio;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
// Transaccional: findByCodigoHash usa bloqueo pesimista, que exige transacción activa.
@org.springframework.transaction.annotation.Transactional
class DispositivoServiceTest {

    static final String DUENO = "dispositivo-dueno@test.com";

    @Autowired DispositivoService servicio;
    @Autowired DispositivoRepository dispositivos;
    @Autowired CodigoVinculacionRepository codigos;

    static String sha256(String texto) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Código con formato XXXX-XXXX y sin caracteres ambiguos")
    void formato_codigo() {
        for (int i = 0; i < 50; i++) {
            String c = servicio.crearVinculacion(DUENO).codigo();
            assertTrue(c.matches("[A-HJ-KM-NP-Z2-9]{4}-[A-HJ-KM-NP-Z2-9]{4}"), c);
        }
    }

    @Test
    @DisplayName("Registro con código válido devuelve token; el token autentica al dispositivo")
    void registro_y_autenticacion() {
        String codigo = servicio.crearVinculacion(DUENO).codigo();
        DispositivoService.Registro r = servicio.registrar(codigo, "ESP32-CAM", "0.1.0");

        assertNotNull(r.deviceId());
        assertNotNull(r.token());
        assertTrue(r.token().length() >= 43, "token de al menos 256 bits en base64url");
        Optional<Dispositivo> d = servicio.autenticar(r.token());
        assertTrue(d.isPresent());
        assertEquals(DUENO, d.get().getPropietarioCorreo());
        assertEquals(r.deviceId(), d.get().getId());
    }

    @Test
    @DisplayName("El código se consume: segundo uso → CODIGO_NO_VALIDO")
    void codigo_de_un_solo_uso() {
        String codigo = servicio.crearVinculacion(DUENO).codigo();
        servicio.registrar(codigo, "ESP32-CAM", "0.1.0");
        DispositivoException e = assertThrows(DispositivoException.class,
                () -> servicio.registrar(codigo, "ESP32-CAM", "0.1.0"));
        assertEquals("CODIGO_NO_VALIDO", e.getCodigo());
    }

    @Test
    @DisplayName("Código inexistente o escrito en minúsculas sin guion: el inexistente falla, el normalizado funciona")
    void normalizacion_y_codigo_inexistente() {
        DispositivoException e = assertThrows(DispositivoException.class,
                () -> servicio.registrar("ZZZZ-ZZZZ", "ESP32-CAM", "0.1.0"));
        assertEquals("CODIGO_NO_VALIDO", e.getCodigo());

        String codigo = servicio.crearVinculacion(DUENO).codigo();
        String escrito = codigo.replace("-", "").toLowerCase();
        assertDoesNotThrow(() -> servicio.registrar(escrito, "ESP32-CAM", "0.1.0"));
    }

    @Test
    @DisplayName("Código caducado → CODIGO_NO_VALIDO, igual que uno inexistente")
    void codigo_caducado() throws Exception {
        String codigo = servicio.crearVinculacion(DUENO).codigo();
        CodigoVinculacion c = codigos.findByCodigoHash(sha256(codigo.replace("-", ""))).orElseThrow();
        c.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        codigos.save(c);

        DispositivoException e = assertThrows(DispositivoException.class,
                () -> servicio.registrar(codigo, "ESP32-CAM", "0.1.0"));
        assertEquals("CODIGO_NO_VALIDO", e.getCodigo());
    }

    @Test
    @DisplayName("El token y el código se guardan solo como hash")
    void sin_secretos_en_claro() throws Exception {
        String codigo = servicio.crearVinculacion(DUENO).codigo();
        DispositivoService.Registro r = servicio.registrar(codigo, "ESP32-CAM", "0.1.0");

        Dispositivo d = dispositivos.findById(r.deviceId()).orElseThrow();
        assertNotEquals(r.token(), d.getTokenHash());
        assertEquals(sha256(r.token()), d.getTokenHash());

        CodigoVinculacion c = codigos.findByCodigoHash(sha256(codigo.replace("-", ""))).orElseThrow();
        assertNotEquals(codigo, c.getCodigoHash());
    }

    @Test
    @DisplayName("Token inventado o vacío no autentica")
    void token_invalido() {
        assertTrue(servicio.autenticar("token-que-no-existe").isEmpty());
        assertTrue(servicio.autenticar("").isEmpty());
        assertTrue(servicio.autenticar(null).isEmpty());
    }

    @Test
    @DisplayName("Rotación: el token anterior deja de valer en la misma operación")
    void rotacion_invalida_el_anterior() {
        String codigo = servicio.crearVinculacion(DUENO).codigo();
        DispositivoService.Registro r = servicio.registrar(codigo, "ESP32-CAM", "0.1.0");

        DispositivoService.Rotacion nuevo = servicio.rotar(r.deviceId());
        assertNotEquals(r.token(), nuevo.token());
        assertTrue(servicio.autenticar(r.token()).isEmpty(), "el token anterior no debe valer");
        assertTrue(servicio.autenticar(nuevo.token()).isPresent());
    }

    @Test
    @DisplayName("Revocación: solo el propietario revoca, y la autenticación falla después")
    void revocacion() {
        String codigo = servicio.crearVinculacion(DUENO).codigo();
        DispositivoService.Registro r = servicio.registrar(codigo, "ESP32-CAM", "0.1.0");

        assertFalse(servicio.revocar("otro-usuario@test.com", r.deviceId()), "ajeno no puede revocar");
        assertTrue(servicio.autenticar(r.token()).isPresent());

        assertTrue(servicio.revocar(DUENO, r.deviceId()));
        assertTrue(servicio.autenticar(r.token()).isEmpty());
    }

    @Test
    @DisplayName("Token caducado no autentica aunque exista")
    void token_caducado() {
        String codigo = servicio.crearVinculacion(DUENO).codigo();
        DispositivoService.Registro r = servicio.registrar(codigo, "ESP32-CAM", "0.1.0");
        Dispositivo d = dispositivos.findById(r.deviceId()).orElseThrow();
        d.setTokenValidoHasta(LocalDateTime.now().minusDays(1));
        dispositivos.save(d);
        assertTrue(servicio.autenticar(r.token()).isEmpty());
    }
}
