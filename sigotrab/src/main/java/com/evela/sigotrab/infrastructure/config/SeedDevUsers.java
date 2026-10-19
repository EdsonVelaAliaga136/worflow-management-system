package com.evela.sigotrab.infrastructure.config;

import com.evela.sigotrab.adapters.persistence.entity.UserJpaEntity;
import com.evela.sigotrab.adapters.persistence.repository.RoleJpaRepository;
import com.evela.sigotrab.adapters.persistence.repository.UserJpaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("dev")
public class SeedDevUsers implements ApplicationRunner {
    private final UserJpaRepository users;
    private final RoleJpaRepository roles;
    private final PasswordEncoder passwordEncoder;

    public SeedDevUsers(UserJpaRepository users, RoleJpaRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        create("admin", "admin123", "Administrador", "ROLE_ADMIN");
        create("registrador", "registrador123", "Registrador",   "ROLE_REGISTRADOR");
        create("publicador",  "publicador123",  "Publicador",    "ROLE_PUBLICADOR");
    }
    private void create(String username, String rawPassword, String fullName, String roleName){
        if (users.findByUsername(username).isPresent())return;
        var role = roles.findByName(roleName)
                .orElseThrow(()->new IllegalStateException("Falta el rol " + roleName));
        var user = new UserJpaEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        users.save(user);
    }
}
