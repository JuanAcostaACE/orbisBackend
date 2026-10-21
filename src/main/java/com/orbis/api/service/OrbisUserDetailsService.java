package com.orbis.api.service;

import com.orbis.api.model.Usuario;
import com.orbis.api.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Integración de ORBIS con Spring Security.
 * Carga el usuario por correo electrónico (username = correo en este sistema).
 */
@Service
public class OrbisUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public OrbisUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        return usuarioRepository.findByCorreo(correo.toLowerCase())
                .filter(Usuario::isActivo)
                .map(u -> User.withUsername(u.getCorreo())
                        .password(u.getHashContrasena())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + correo));
    }
}
