package com.evela.sigotrab.infrastructure.config;

import com.evela.sigotrab.adapters.persistence.entity.UserJpaEntity;
import com.evela.sigotrab.adapters.persistence.repository.RoleJpaRepository;
import com.evela.sigotrab.adapters.persistence.repository.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Siembra el primer administrador del perfil {@code lan} (ADR-0009).
 *
 * <p>Solo actúa cuando la base de datos todavía no tiene ningún usuario. Si ya existe
 * al menos uno, no hace nada: así el arranque es idempotente y no requiere conservar la
 * contraseña en el entorno una vez creado el administrador.</p>
 */
@Component
@Profile("lan")
public class SeedAdminUser implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedAdminUser.class);
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final UserJpaRepository users;
    private final RoleJpaRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties admin;

    public SeedAdminUser(UserJpaRepository users, RoleJpaRepository roles,
                         PasswordEncoder passwordEncoder, AdminProperties admin) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.admin = admin;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.info("SeedAdminUser: ya existen usuarios; no se crea el administrador inicial.");
            return;
        }

        requireText(admin.username(), "sigotrab.admin.username / SIGOTRAB_ADMIN_USERNAME");
        requireText(admin.password(), "sigotrab.admin.password / SIGOTRAB_ADMIN_PASSWORD");
        String fullName = StringUtils.hasText(admin.fullName()) ? admin.fullName() : "Administrador";

        var role = roles.findByName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el rol " + ADMIN_ROLE + "; revisa la migración V2__seed_roles.sql"));

        var user = new UserJpaEntity();
        user.setUsername(admin.username().trim());
        user.setPassword(passwordEncoder.encode(admin.password()));
        user.setFullName(fullName);
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        users.save(user);

        log.info("SeedAdminUser: administrador inicial '{}' creado.", user.getUsername());
    }

    private static void requireText(String value, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Falta la propiedad '" + property + "' para crear el administrador inicial "
                            + "en el perfil 'lan'.");
        }
    }
}
