package com.orbis.api.controller;

import com.orbis.api.config.SesionMovilAuthFilter;
import com.orbis.api.dto.AuthResponse;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.TokenSesionResponse;
import com.orbis.api.service.AuthService;
import com.orbis.api.service.BloqueoCuentaService;
import com.orbis.api.service.CuentaBloqueadaException;
import com.orbis.api.service.SesionMovilService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Inicio de sesión sin cookies para la app móvil (Bearer).
 *
 * Públicos: POST /auth/token. Autenticados con token: GET /auth/token/yo, POST /auth/token/logout.
 * Reutiliza el mismo límite por cuenta (BloqueoCuentaService) y la misma autenticación
 * (AuthenticationManager) que POST /auth/login. No crea sesión HTTP.
 */
@RestController
@RequestMapping("/auth/token")
public class SesionMovilController {

    private final AuthService           authService;
    private final AuthenticationManager authManager;
    private final BloqueoCuentaService  bloqueoCuenta;
    private final SesionMovilService    sesionMovil;

    public SesionMovilController(
            AuthService authService,
            AuthenticationManager authManager,
            BloqueoCuentaService bloqueoCuenta,
            SesionMovilService sesionMovil) {
        this.authService = authService;
        this.authManager = authManager;
        this.bloqueoCuenta = bloqueoCuenta;
        this.sesionMovil = sesionMovil;
    }

    /** Valida credenciales y devuelve un token de acceso de 8 horas. */
    @PostMapping
    public ResponseEntity<?> emitir(@Valid @RequestBody LoginRequest request) {
        String correo = AuthService.normalizarCorreo(request.correo());

        // Igual que /auth/login: cuenta bloqueada responde 429 antes de buscar el usuario.
        long segundosBloqueo = bloqueoCuenta.segundosBloqueo(correo);
        if (segundosBloqueo > 0) {
            throw new CuentaBloqueadaException(segundosBloqueo);
        }

        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.correo(), request.contrasena()));
        } catch (AuthenticationException e) {
            bloqueoCuenta.registrarFallo(correo);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "CREDENCIALES_INCORRECTAS"));
        }
        bloqueoCuenta.limpiar(correo);

        AuthResponse usuario = authService.validarCredenciales(request);
        SesionMovilService.Emision emision = sesionMovil.crear(usuario.correo());
        return ResponseEntity.ok(new TokenSesionResponse(
                emision.accessToken(),
                "Bearer",
                SesionMovilService.VALIDEZ.getSeconds(),
                usuario.correo(),
                usuario.nombre()));
    }

    /** Correo de la sesión móvil del token. Un token de dispositivo o una cookie web dan 401. */
    @GetMapping("/yo")
    public ResponseEntity<Map<String, String>> yo(HttpServletRequest request, Authentication auth) {
        if (request.getAttribute(SesionMovilAuthFilter.ATRIBUTO_SESION_MOVIL_ID) == null || auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("correo", auth.getName()));
    }

    /** Revoca el token usado en la petición. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Object id = request.getAttribute(SesionMovilAuthFilter.ATRIBUTO_SESION_MOVIL_ID);
        if (id == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        sesionMovil.revocar((String) id);
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
