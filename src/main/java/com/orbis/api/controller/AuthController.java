package com.orbis.api.controller;

import com.orbis.api.dto.AuthResponse;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegistroRequest;
import com.orbis.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints de autenticación.
 * Públicos: /auth/registro, /auth/login.
 * Autenticado: /auth/logout, /auth/me.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService           authService;
    private final AuthenticationManager authManager;

    public AuthController(AuthService authService, AuthenticationManager authManager) {
        this.authService = authService;
        this.authManager = authManager;
    }

    /** Registra un nuevo usuario. Devuelve 201 Created. */
    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registro(
            @Valid @RequestBody RegistroRequest request) {
        AuthResponse response = authService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Login: valida credenciales, crea sesión y establece SecurityContext. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        // 1. Autenticar con Spring Security (lanza BadCredentialsException si falla)
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.correo(), request.contrasena())
        );

        // 2. Guardar en SecurityContext y en sesión HTTP
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx
        );
        // Sesión de 8 horas
        session.setMaxInactiveInterval(8 * 3600);

        // 3. Devolver datos del usuario (sin contraseña)
        AuthResponse response = authService.validarCredenciales(request);
        return ResponseEntity.ok(response);
    }

    /** Cierra la sesión e invalida la cookie JSESSIONID. */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada correctamente"));
    }

    /** Devuelve el usuario actualmente autenticado. */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("correo", auth.getName()));
    }
}
