package com.orbis.api;

import com.orbis.api.dto.RegistroRequest;
import com.orbis.api.dto.RegistroResponse;
import com.orbis.api.model.Usuario;
import com.orbis.api.repository.UsuarioRepository;
import com.orbis.api.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del camino de registro: el duplicado y la carrera (UNIQUE)
 * devuelven la misma respuesta que un alta nueva, y ambos caminos calculan el hash.
 */
class AuthServiceRegistroTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final PasswordEncoder   enc  = mock(PasswordEncoder.class);
    private final AuthService       svc  = new AuthService(repo, enc);

    private static final RegistroRequest REQ =
            new RegistroRequest("Nombre", "Correo@Test.com", "contraseña_segura_123");

    @Test
    @DisplayName("Duplicado: no inserta, calcula el hash igualmente y responde como alta nueva")
    void duplicado_no_inserta_y_hace_hash() {
        when(repo.existsByCorreo("correo@test.com")).thenReturn(true);
        when(enc.encode(any())).thenReturn("hash");

        RegistroResponse r = svc.registrar(REQ);

        verify(enc, times(1)).encode(REQ.contrasena());
        verify(repo, never()).saveAndFlush(any(Usuario.class));
        assertThat(r).isEqualTo(new RegistroResponse(201, AuthService.MENSAJE_REGISTRO));
    }

    @Test
    @DisplayName("Alta nueva: inserta una vez y responde con el mismo cuerpo")
    void alta_nueva_inserta() {
        when(repo.existsByCorreo("correo@test.com")).thenReturn(false);
        when(enc.encode(any())).thenReturn("hash");

        RegistroResponse r = svc.registrar(REQ);

        verify(repo, times(1)).saveAndFlush(any(Usuario.class));
        assertThat(r).isEqualTo(new RegistroResponse(201, AuthService.MENSAJE_REGISTRO));
    }

    @Test
    @DisplayName("Carrera: el insert choca con UNIQUE y se responde igual, sin lanzar excepción")
    void carrera_unique_responde_igual() {
        when(repo.existsByCorreo("correo@test.com")).thenReturn(false);
        when(enc.encode(any())).thenReturn("hash");
        when(repo.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("uk_usuarios_correo"));

        RegistroResponse r = svc.registrar(REQ);

        assertThat(r).isEqualTo(new RegistroResponse(201, AuthService.MENSAJE_REGISTRO));
    }
}
