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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.evela.sigotrab.application.port.out.AuditPort;
import com.evela.sigotrab.application.port.out.JobOfferRepository;
import com.evela.sigotrab.domain.exception.InvalidJobStateException;
import com.evela.sigotrab.domain.exception.JobNotFoundException;
import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.AuditAction;
import com.evela.sigotrab.domain.model.AuditRecord;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobOfferContent;
import com.evela.sigotrab.domain.model.NewJobOffer;

@ExtendWith(MockitoExtension.class)
class UpdateJobOfferServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T09:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();
    private static final ActorInfo ACTOR =
            new ActorInfo(UUID.randomUUID(), "registrador", "127.0.0.1");

    @Mock JobOfferRepository offers;
    @Mock AuditPort audit;

    private UpdateJobOfferService service;

    @BeforeEach
    void setUp() {
        service = new UpdateJobOfferService(offers, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static JobOffer registeredOffer() {
        return JobOffer.register(
                new NewJobOffer("Analista", "ACME", "Descripcion", "Requisitos",
                        "Remoto", "1000", "Completa", "https://example.com/1",
                        "portal", "ext-1", NOW),
                OWNER, NOW);
    }

    private static JobOfferContent content(String title) {
        return new JobOfferContent(title, "ACME", "Descripcion nueva", "Requisitos nuevos",
                "Hibrido", "1500", "Media jornada", "https://example.com/2");
    }

    @Test
    void updatesRegisteredOfferAndAuditsOldAndNewValues() {
        JobOffer offer = registeredOffer();
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));

        JobOffer result = service.update(offer.getId(), content("Analista senior"), ACTOR);

        assertThat(result.getTitle()).isEqualTo("Analista senior");
        assertThat(result.getSalary()).isEqualTo("1500");
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result.getUpdatedBy()).isEqualTo(ACTOR.userId());
        verify(offers).save(offer);

        var captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).record(captor.capture());
        AuditRecord event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(event.entityType()).isEqualTo("JOB_OFFER");
        assertThat(event.entityId()).isEqualTo(offer.getId().toString());
        assertThat(event.success()).isTrue();
        assertThat(event.actor()).isEqualTo(ACTOR);
        assertThat(event.oldValues())
                .containsEntry("title", "Analista")
                .containsEntry("salary", "1000");
        assertThat(event.newValues())
                .containsEntry("title", "Analista senior")
                .containsEntry("salary", "1500");
    }

    @Test
    void unknownOfferThrowsNotFoundWithoutSideEffects() {
        UUID id = UUID.randomUUID();
        when(offers.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, content("X"), ACTOR))
                .isInstanceOf(JobNotFoundException.class);

        verify(offers, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void offerOutsideRegisteredIsRejectedWithoutSideEffects() {
        JobOffer approved = registeredOffer();
        approved.approve(OWNER, NOW);
        when(offers.findById(approved.getId())).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.update(approved.getId(), content("X"), ACTOR))
                .isInstanceOf(InvalidJobStateException.class);

        assertThat(approved.getTitle()).isEqualTo("Analista");
        verify(offers, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.update(null, content("X"), ACTOR));
        assertThatNullPointerException()
                .isThrownBy(() -> service.update(UUID.randomUUID(), null, ACTOR));
        assertThatNullPointerException()
                .isThrownBy(() -> service.update(UUID.randomUUID(), content("X"), null));

        verifyNoInteractions(offers, audit);
    }
}
