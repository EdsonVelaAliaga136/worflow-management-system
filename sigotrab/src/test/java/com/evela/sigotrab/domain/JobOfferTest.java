package com.evela.sigotrab.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.evela.sigotrab.domain.exception.InvalidJobStateException;
import com.evela.sigotrab.domain.exception.RejectionReasonRequiredException;
import com.evela.sigotrab.domain.exception.UnauthorizedJobOperationException;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobOfferContent;
import com.evela.sigotrab.domain.model.JobStatus;
import com.evela.sigotrab.domain.model.NewJobOffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JobOfferTest {
    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");
    private static final UUID REGISTRADOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PUBLICADOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTRO = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private JobOffer registeredOffer() {
        return JobOffer.register(new NewJobOffer(
                "Analista de datos",
                "ACME",
                "Descripción",
                "SQL y Java",
                "Remoto",
                "1000 USD",
                "Tiempo completo",
                "https://example.com/oferta/1",
                "portal",
                "ext-1",
                T0), REGISTRADOR, T0);
    }

    @Test
    void register_shouldInitializeCorrectly() {
        var offer = registeredOffer();

        assertThat(offer.getId()).isNotNull();
        assertThat(offer.getStatus()).isEqualTo(JobStatus.REGISTERED);
        assertThat(offer.getCreatedAt()).isEqualTo(T0);
        assertThat(offer.getCreatedBy()).isEqualTo(REGISTRADOR);
        assertThat(offer.getUpdatedAt()).isEqualTo(T0);
        assertThat(offer.getUpdatedBy()).isEqualTo(REGISTRADOR);
        assertThat(offer.getApprovedAt()).isNull();
        assertThat(offer.getPublishedAt()).isNull();
        assertThat(offer.getAssignedUserId()).isNull();
        assertThat(offer.getLockedAt()).isNull();
        assertThat(offer.getLockExpiresAt()).isNull();
    }

    @Test
    void register_withBlankTitle_shouldThrowException() {
        var invalidData = new NewJobOffer(
                "   ", "ACME", "Desc", "Req", "Remoto", "1000", "Full", "https://url.com", "portal", "ext-1", T0);
        assertThatThrownBy(() -> JobOffer.register(invalidData, REGISTRADOR, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateContent_inRegisteredState_shouldUpdateFields() {
        var offer = registeredOffer();
        var newContent = new JobOfferContent(
                "Nuevo Título", "ACME", "Nueva Desc", "Java 17", "Híbrido", "1500 USD", "Media jornada",
                "https://example.com/2");

        offer.updateContent(newContent, REGISTRADOR, T0.plusSeconds(30));

        assertThat(offer.getTitle()).isEqualTo("Nuevo Título");
        assertThat(offer.getLocation()).isEqualTo("Híbrido");
        assertThat(offer.getUpdatedAt()).isEqualTo(T0.plusSeconds(30));
        // La fuente original no debe cambiar
        assertThat(offer.getExternalReference()).isEqualTo("ext-1");
    }

    @Test
    void updateContent_inApprovedState_shouldThrowException() {
        var offer = registeredOffer();
        offer.approve(REGISTRADOR, T0.plusSeconds(10));

        var newContent = new JobOfferContent(
                "Intento cambio", "ACME", "Desc", "Req", null, null, null, "https://example.com/2");

        assertThatThrownBy(() -> offer.updateContent(newContent, REGISTRADOR, T0.plusSeconds(20)))
                .isInstanceOf(InvalidJobStateException.class);
    }

    @Test
    void approve_fromRegistered_shouldSucceed() {
        var offer = registeredOffer();
        offer.approve(REGISTRADOR, T0.plusSeconds(10));

        assertThat(offer.getStatus()).isEqualTo(JobStatus.APPROVED);
        assertThat(offer.getApprovedAt()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void reject_withBlankReason_shouldThrowException() {
        var offer = registeredOffer();
        assertThatThrownBy(() -> offer.reject(REGISTRADOR, "   ", T0.plusSeconds(10)))
                .isInstanceOf(RejectionReasonRequiredException.class);
    }

    @Test
    void reject_withValidReason_shouldSucceed() {
        var offer = registeredOffer();
        offer.reject(REGISTRADOR, "Falta información clave", T0.plusSeconds(10));

        assertThat(offer.getStatus()).isEqualTo(JobStatus.REJECTED);
    }

    @Test
    void acquire_fromApproved_shouldSetLockAndProgressState() {
        var offer = registeredOffer();
        offer.approve(REGISTRADOR, T0.plusSeconds(10));

        var lockDuration = Duration.ofMinutes(15);
        offer.acquire(PUBLICADOR, lockDuration, T0.plusSeconds(20));

        assertThat(offer.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(offer.getAssignedUserId()).isEqualTo(PUBLICADOR);
        assertThat(offer.getLockedAt()).isEqualTo(T0.plusSeconds(20));
        assertThat(offer.getLockExpiresAt()).isEqualTo(T0.plusSeconds(20).plus(lockDuration));
    }

    @Test
    void publish_byAssignedUser_shouldSucceedAndClearLock() {
        var offer = registeredOffer();
        offer.approve(REGISTRADOR, T0.plusSeconds(10));
        offer.acquire(PUBLICADOR, Duration.ofMinutes(15), T0.plusSeconds(20));

        offer.publish(PUBLICADOR, T0.plusSeconds(30));

        assertThat(offer.getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(offer.getPublishedAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(offer.getAssignedUserId()).isNull();
        assertThat(offer.getLockedAt()).isNull();
        assertThat(offer.getLockExpiresAt()).isNull();
    }

    @Test
    void publish_byAnotherUser_shouldThrowUnauthorizedException() {
        var offer = registeredOffer();
        offer.approve(REGISTRADOR, T0.plusSeconds(10));
        offer.acquire(PUBLICADOR, Duration.ofMinutes(15), T0.plusSeconds(20));

        // Intenta publicar un usuario distinto al que tiene el lock
        assertThatThrownBy(() -> offer.publish(OTRO, T0.plusSeconds(30)))
                .isInstanceOf(UnauthorizedJobOperationException.class);

        // El estado debe mantenerse intacto en IN_PROGRESS
        assertThat(offer.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
    }

    @Test
    void isLockExpired_shouldBeTrueIfTimePassed() {
        var offer = registeredOffer();
        offer.approve(REGISTRADOR, T0.plusSeconds(10));
        
        // Se adquiere UNA SOLA VEZ en T0 + 20s con duración de 10 minutos
        offer.acquire(PUBLICADOR, Duration.ofMinutes(10), T0.plusSeconds(20)); 

        // 1. Consultamos ANTES de que expire (a los 2 minutos -> T0 + 120s) -> Debe ser false
        assertThat(offer.isLockExpired(T0.plusSeconds(2 * 60))).isFalse();

        // 2. Consultamos DESPUÉS de que expire (a los 11 minutos -> T0 + 660s) -> Debe ser true
        assertThat(offer.isLockExpired(T0.plusSeconds(11 * 60))).isTrue();
    }
}
