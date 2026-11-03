package com.evela.sigotrab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.evela.sigotrab.application.port.out.AuditPort;
import com.evela.sigotrab.application.port.out.JobOfferRepository;
import com.evela.sigotrab.domain.exception.DuplicateJobOfferException;
import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.AuditAction;
import com.evela.sigotrab.domain.model.AuditRecord;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;
import com.evela.sigotrab.domain.model.NewJobOffer;

@ExtendWith(MockitoExtension.class)
class RegisterJobOfferServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final ActorInfo ACTOR =
            new ActorInfo(UUID.randomUUID(), "registrador", "127.0.0.1");

    @Mock JobOfferRepository offers;
    @Mock AuditPort audit;

    private RegisterJobOfferService service;

    @BeforeEach
    void setUp() {
        service = new RegisterJobOfferService(offers, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static NewJobOffer data(String sourceName, String externalReference) {
        return new NewJobOffer(
                "Analista de datos", "ACME", "Descripcion", "Requisitos",
                "Remoto", "40.000", "Jornada completa",
                "https://example.com/1", sourceName, externalReference, NOW);
    }

    @Test
    void registersOfferAsRegisteredAndAuditsCreate() {
        when(offers.existsBySourceReference("portal", "ext-1")).thenReturn(false);

        JobOffer offer = service.register(data("portal", "ext-1"), ACTOR);

        assertThat(offer.getStatus()).isEqualTo(JobStatus.REGISTERED);
        assertThat(offer.getCreatedAt()).isEqualTo(NOW);
        assertThat(offer.getCreatedBy()).isEqualTo(ACTOR.userId());
        verify(offers).save(offer);

        var captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).record(captor.capture());
        AuditRecord event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.CREATE);
        assertThat(event.entityType()).isEqualTo("JOB_OFFER");
        assertThat(event.entityId()).isEqualTo(offer.getId().toString());
        assertThat(event.actor()).isEqualTo(ACTOR);
        assertThat(event.success()).isTrue();
        assertThat(event.newValues())
                .containsEntry("status", "REGISTERED")
                .containsEntry("sourceName", "portal")
                .containsEntry("externalReference", "ext-1");
    }

    @Test
    void rejectsDuplicateWhenSourceAndReferenceMatch() {
        when(offers.existsBySourceReference("portal", "ext-1")).thenReturn(true);

        assertThatThrownBy(() -> service.register(data("portal", "ext-1"), ACTOR))
                .isInstanceOf(DuplicateJobOfferException.class)
                .hasMessageContaining("portal")
                .hasMessageContaining("ext-1");

        verify(offers, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void skipsDuplicateCheckWhenSourceNameIsMissing() {
        JobOffer offer = service.register(data(null, "ext-1"), ACTOR);

        assertThat(offer.getStatus()).isEqualTo(JobStatus.REGISTERED);
        verify(offers, never()).existsBySourceReference(any(), any());
        verify(offers).save(offer);
        verify(audit).record(any());
    }

    @Test
    void skipsDuplicateCheckWhenExternalReferenceIsBlank() {
        JobOffer offer = service.register(data("portal", "   "), ACTOR);

        verify(offers, never()).existsBySourceReference(any(), any());
        verify(offers).save(offer);
    }

    @Test
    void rejectsNullData() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.register(null, ACTOR))
                .withMessage("data");
    }

    @Test
    void rejectsNullActor() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.register(data("portal", "ext-1"), null))
                .withMessage("actor");
    }
}
