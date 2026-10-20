package com.evela.sigotrab.infrastructure.config;

import com.evela.sigotrab.adapters.persistence.entity.RoleJpaEntity;
import com.evela.sigotrab.adapters.persistence.entity.UserJpaEntity;
import com.evela.sigotrab.adapters.persistence.repository.RoleJpaRepository;
import com.evela.sigotrab.adapters.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedAdminUserTest {

    @Test
    void noHaceNadaSiYaExisteAlgunUsuario() {
        var users = mock(UserJpaRepository.class);
        var roles = mock(RoleJpaRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(users.count()).thenReturn(1L);

        new SeedAdminUser(users, roles, encoder,
                new AdminProperties("admin", "secreto", "Administrador")).run(null);

        verify(users, never()).save(any());
    }

    @Test
    void fallaConMensajeClaroSiLaBdVaciaNoTienePassword() {
        var users = mock(UserJpaRepository.class);
        var roles = mock(RoleJpaRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(users.count()).thenReturn(0L);

        assertThatThrownBy(() -> new SeedAdminUser(users, roles, encoder,
                new AdminProperties("admin", "", "Administrador")).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SIGOTRAB_ADMIN_PASSWORD");
    }

    @Test
    void creaElAdminConRolAdminYPasswordHasheada() {
        var users = mock(UserJpaRepository.class);
        var roles = mock(RoleJpaRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var role = new RoleJpaEntity(UUID.randomUUID(), "ROLE_ADMIN");

        when(users.count()).thenReturn(0L);
        when(roles.findByName("ROLE_ADMIN")).thenReturn(Optional.of(role));
        when(encoder.encode("secreto")).thenReturn("{bcrypt}hash");

        new SeedAdminUser(users, roles, encoder,
                new AdminProperties("admin", "secreto", "Administrador")).run(null);

        var captor = ArgumentCaptor.forClass(UserJpaEntity.class);
        verify(users).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(saved.getFullName()).isEqualTo("Administrador");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getRoles()).containsExactly(role);
    }
}
