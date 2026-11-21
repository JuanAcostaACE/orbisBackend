package com.orbis.api;

import com.orbis.api.dto.RegisterRequest;
import com.orbis.api.dto.RegisterResponse;
import com.orbis.api.model.UserAccount;
import com.orbis.api.repository.UserAccountRepository;
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
class AuthServiceRegisterTest {

    private final UserAccountRepository repo = mock(UserAccountRepository.class);
    private final PasswordEncoder   enc  = mock(PasswordEncoder.class);
    private final AuthService       svc  = new AuthService(repo, enc);

    private static final RegisterRequest REQ =
            new RegisterRequest("Nombre", "Correo@Test.com", "contraseña_segura_123");

    @Test
    @DisplayName("Duplicado: no inserta, calcula el hash igualmente y responde como alta nueva")
    void duplicate_does_not_insert_and_hashes() {
        when(repo.existsByEmail("correo@test.com")).thenReturn(true);
        when(enc.encode(any())).thenReturn("hash");

        RegisterResponse r = svc.register(REQ);

        verify(enc, times(1)).encode(REQ.password());
        verify(repo, never()).saveAndFlush(any(UserAccount.class));
        assertThat(r).isEqualTo(new RegisterResponse(201, AuthService.REGISTER_MESSAGE));
    }

    @Test
    @DisplayName("Alta nueva: inserta una vez y responde con el mismo cuerpo")
    void new_signup_inserts() {
        when(repo.existsByEmail("correo@test.com")).thenReturn(false);
        when(enc.encode(any())).thenReturn("hash");

        RegisterResponse r = svc.register(REQ);

        verify(repo, times(1)).saveAndFlush(any(UserAccount.class));
        assertThat(r).isEqualTo(new RegisterResponse(201, AuthService.REGISTER_MESSAGE));
    }

    @Test
    @DisplayName("Carrera: el insert choca con UNIQUE y se responde igual, sin lanzar excepción")
    void unique_race_responds_same() {
        when(repo.existsByEmail("correo@test.com")).thenReturn(false);
        when(enc.encode(any())).thenReturn("hash");
        when(repo.saveAndFlush(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        RegisterResponse r = svc.register(REQ);

        assertThat(r).isEqualTo(new RegisterResponse(201, AuthService.REGISTER_MESSAGE));
    }
}
