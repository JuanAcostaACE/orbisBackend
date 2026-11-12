package com.orbis.api.service;

import com.orbis.api.dto.AuthResponse;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegistroRequest;
import com.orbis.api.dto.RegistroResponse;
import com.orbis.api.model.Usuario;
import com.orbis.api.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Servicio de autenticación y registro de usuarios.
 * Responsabilidades:
 *  - Registrar nuevos usuarios (hash BCrypt) sin revelar si el correo ya existe.
 *  - Validar credenciales en el login.
 *  - Nunca exponer el hash de contraseña en respuestas.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Mensaje único de registro: no promete envío de correo ni confirma existencia. */
    public static final String MENSAJE_REGISTRO =
            "Solicitud procesada. Inicia sesión para continuar.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder   passwordEncoder;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder   = passwordEncoder;
    }

    /**
     * Registra un nuevo usuario de forma anti-enumeración.
     *
     * Devuelve siempre la misma respuesta, tanto si el correo es nuevo como si ya estaba
     * registrado. En ambos caminos se calcula el hash BCrypt (mismo coste de tiempo), pero
     * solo el camino nuevo inserta. Si dos peticiones simultáneas pasan la comprobación y la
     * segunda choca con la restricción UNIQUE, también se devuelve la respuesta genérica.
     *
     * No es transaccional a propósito: una excepción de restricción dentro de una transacción
     * abierta la marcaría como rollback-only y el commit fallaría.
     */
    public RegistroResponse registrar(RegistroRequest request) {
        String correo = normalizarCorreo(request.correo());
        String hash   = passwordEncoder.encode(request.contrasena());

        if (usuarioRepository.existsByCorreo(correo)) {
            return respuestaRegistro();
        }

        Usuario u = new Usuario();
        u.setNombre(request.nombre().trim());
        u.setCorreo(correo);
        u.setHashContrasena(hash);
        u.setActivo(true);

        try {
            usuarioRepository.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            // Carrera: otro registro con el mismo correo se insertó entre la comprobación y el insert.
            log.debug("Registro con restricción UNIQUE; se responde igual que un duplicado");
        }
        return respuestaRegistro();
    }

    /**
     * Valida las credenciales y devuelve el usuario.
     * Spring Security llama a este método a través de OrbisUserDetailsService;
     * aquí se usa para el controller de login que necesita el AuthResponse.
     * @throws BadCredentialsException si el correo no existe o la contraseña no coincide.
     */
    @Transactional(readOnly = true)
    public AuthResponse validarCredenciales(LoginRequest request) {
        Usuario u = usuarioRepository.findByCorreo(normalizarCorreo(request.correo()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));

        if (!u.isActivo()) {
            throw new BadCredentialsException("Cuenta desactivada");
        }

        if (!passwordEncoder.matches(request.contrasena(), u.getHashContrasena())) {
            throw new BadCredentialsException("Credenciales incorrectas");
        }

        return toResponse(u, "Login exitoso");
    }

    /** Normaliza el correo para comparaciones y almacenamiento: sin espacios y en minúsculas. */
    public static String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private static RegistroResponse respuestaRegistro() {
        return new RegistroResponse(201, MENSAJE_REGISTRO);
    }

    /** Convierte un Usuario a AuthResponse (sin hash). */
    private AuthResponse toResponse(Usuario u, String mensaje) {
        return new AuthResponse(u.getId(), u.getNombre(), u.getCorreo(), mensaje);
    }
}
