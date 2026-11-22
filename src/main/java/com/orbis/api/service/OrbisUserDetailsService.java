package com.orbis.api.service;

import com.orbis.api.model.UserAccount;
import com.orbis.api.repository.UserAccountRepository;
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

    private final UserAccountRepository userAccountRepository;

    public OrbisUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userAccountRepository.findByEmail(email.toLowerCase())
                .filter(UserAccount::isActive)
                .map(u -> User.withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));
    }
}
