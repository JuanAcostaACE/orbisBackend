package com.orbis.api.service;

import com.orbis.api.dto.AuthResponse;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegistroRequest;
import com.orbis.api.model.Usuario;
import com.orbis.api.repository.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de autenticación y registro de usuarios.
 * Responsabilidades:
 *  - Registrar nuevos usuarios (hash BCrypt, verificación de duplicados).
 *  - Validar credenciales en el login.
 *  - Nunca exponer el hash de contraseña en respuestas.
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder   passwordEncoder;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder   = passwordEncoder;
    }

    /**
     * Registra un nuevo usuario.
     * @throws IllegalArgumentException si el correo ya está registrado.
     */
    @Transactional
    public AuthResponse registrar(RegistroRequest request) {
        if (usuarioRepository.existsByCorreo(request.correo().toLowerCase())) {
            throw new IllegalArgumentException("El correo ya está registrado");
        }

        Usuario u = new Usuario();
        u.setNombre(request.nombre().trim());
        u.setCorreo(request.correo().toLowerCase().trim());
        u.setHashContrasena(passwordEncoder.encode(request.contrasena()));
        u.setActivo(true);

        Usuario guardado = usuarioRepository.save(u);
        return toResponse(guardado, "Registro exitoso");
    }

    /**
     * Valida las credenciales y devuelve el usuario.
     * Spring Security llama a este método a través de OrbisUserDetailsService;
     * aquí se usa para el controller de login que necesita el AuthResponse.
     * @throws BadCredentialsException si el correo no existe o la contraseña no coincide.
     */
    @Transactional(readOnly = true)
    public AuthResponse validarCredenciales(LoginRequest request) {
        Usuario u = usuarioRepository.findByCorreo(request.correo().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));

        if (!u.isActivo()) {
            throw new BadCredentialsException("Cuenta desactivada");
        }

        if (!passwordEncoder.matches(request.contrasena(), u.getHashContrasena())) {
            throw new BadCredentialsException("Credenciales incorrectas");
        }

        return toResponse(u, "Login exitoso");
    }

    /** Convierte un Usuario a AuthResponse (sin hash). */
    private AuthResponse toResponse(Usuario u, String mensaje) {
        return new AuthResponse(u.getId(), u.getNombre(), u.getCorreo(), mensaje);
    }
}
