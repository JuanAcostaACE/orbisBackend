package com.orbis.api.controller;

import com.orbis.api.dto.AuthResponse;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegistroRequest;
import com.orbis.api.dto.RegistroResponse;
import com.orbis.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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
    private final String  cookieNombre;
    private final boolean cookieSecure;
    private final String  cookieSameSite;

    public AuthController(
            AuthService authService,
            AuthenticationManager authManager,
            @Value("${server.servlet.session.cookie.name:ORBIS_SID}") String cookieNombre,
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookieSecure,
            @Value("${server.servlet.session.cookie.same-site:Strict}") String cookieSameSite) {
        this.authService = authService;
        this.authManager = authManager;
        this.cookieNombre = cookieNombre;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    /**
     * Registro anti-enumeración: 201 con el mismo cuerpo exista o no el correo.
     * Los datos inválidos siguen devolviendo 400 (no revelan existencia).
     */
    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> registro(
            @Valid @RequestBody RegistroRequest request) {
        RegistroResponse response = authService.registrar(request);
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

        // 2. Protección contra session fixation: si ya existía una sesión (p. ej. anónima
        //    o creada por un atacante), se cambia su ID antes de asociarla al usuario.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        // 3. Guardar en SecurityContext y en sesión HTTP
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

    /**
     * Cierra la sesión e invalida la cookie de sesión (ORBIS_SID).
     * Emite una cookie expirada con los mismos atributos que la de login
     * para que el navegador la sustituya y la elimine.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();

        ResponseCookie cookieExpirada = ResponseCookie.from(cookieNombre, "")
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieExpirada.toString())
                .body(Map.of("mensaje", "Sesión cerrada correctamente"));
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
