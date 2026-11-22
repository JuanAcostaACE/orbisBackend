package com.orbis.api.controller;

import com.orbis.api.dto.AuthResponse;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegisterRequest;
import com.orbis.api.dto.RegisterResponse;
import com.orbis.api.service.AuthService;
import com.orbis.api.service.AccountLockoutService;
import com.orbis.api.service.AccountLockedException;
import org.springframework.security.core.AuthenticationException;
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
 * Públicos: /auth/register, /auth/login.
 * Autenticado: /auth/logout, /auth/me.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService           authService;
    private final AuthenticationManager authManager;
    private final AccountLockoutService  accountLockout;
    private final String  cookieName;
    private final boolean cookieSecure;
    private final String  cookieSameSite;

    public AuthController(
            AuthService authService,
            AuthenticationManager authManager,
            AccountLockoutService accountLockout,
            @Value("${server.servlet.session.cookie.name:ORBIS_SID}") String cookieName,
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookieSecure,
            @Value("${server.servlet.session.cookie.same-site:Strict}") String cookieSameSite) {
        this.authService = authService;
        this.authManager = authManager;
        this.accountLockout = accountLockout;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    /**
     * Registro anti-enumeración: 201 con el mismo cuerpo exista o no el correo.
     * Los datos inválidos siguen devolviendo 400 (no revelan existencia).
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Login: valida credenciales, crea sesión y establece SecurityContext. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        // 0. Límite por cuenta: se comprueba ANTES de buscar el usuario, así una cuenta
        //    bloqueada y una inexistente responden igual (429, mismo cuerpo).
        String email = AuthService.normalizeEmail(request.email());
        long lockoutSeconds = accountLockout.lockoutSeconds(email);
        if (lockoutSeconds > 0) {
            throw new AccountLockedException(lockoutSeconds);
        }

        // 1. Autenticar con Spring Security. Cada fallo cuenta para la cuenta.
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            accountLockout.recordFailure(email);
            throw e;
        }
        // Login correcto: se borra el contador de la cuenta
        accountLockout.clear(email);

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
        AuthResponse response = authService.validateCredentials(request);
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

        ResponseCookie expiredCookie = ResponseCookie.from(cookieName, "")
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(Map.of("message", "Sesión cerrada correctamente"));
    }

    /** Devuelve el usuario actualmente autenticado. */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("email", auth.getName()));
    }
}
