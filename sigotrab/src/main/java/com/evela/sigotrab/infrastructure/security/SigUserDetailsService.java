package com.evela.sigotrab.infrastructure.security;

import com.evela.sigotrab.adapters.persistence.entity.UserJpaEntity;
import com.evela.sigotrab.adapters.persistence.repository.UserJpaRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class SigUserDetailsService implements UserDetailsService {
    private final UserJpaRepository userJpaRepository;

    public SigUserDetailsService(UserJpaRepository userJpaRepository){
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserJpaEntity user = userJpaRepository.findByUsername(username).orElseThrow(()->new UsernameNotFoundException(
                "Usuario no encontrado: " + username
        ));
        var authorities = user.getRoles().stream()
                .map(r->new SimpleGrantedAuthority(r.getName()))
                .collect(Collectors.toSet());
        return new SigUser(user.getId(), user.getUsername(), user.getPassword(), user.isEnabled(), authorities);
    }
}
