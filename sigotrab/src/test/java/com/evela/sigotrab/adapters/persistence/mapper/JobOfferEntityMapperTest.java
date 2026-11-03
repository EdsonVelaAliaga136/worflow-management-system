package com.evela.sigotrab.adapters.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.evela.sigotrab.adapters.persistence.entity.JobOfferJpaEntity;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobOfferContent;
import com.evela.sigotrab.domain.model.JobStatus;
import com.evela.sigotrab.domain.model.NewJobOffer;

class JobOfferEntityMapperTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private final JobOfferEntityMapper mapper = new JobOfferEntityMapper();

    @Test
    void toDomainMapsEveryColumn() {
        var e = new JobOfferJpaEntity();
        e.setId(UUID.randomUUID());
        e.setTitle("Analista de datos");
        e.setCompany("ACME");
        e.setDescription("Descripcion");
        e.setRequirements("Requisitos");
        e.setLocation("Remoto");
        e.setSalary("40.000");
        e.setEmploymentType("Jornada completa");
        e.setOriginalSourceUrl("https://example.com/1");
        e.setSourceName("portal");
        e.setExternalReference("ext-1");
        e.setExtractedAt(NOW);
        e.setStatus(JobStatus.REGISTERED);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);

        JobOffer d = mapper.toDomain(e);

        assertThat(d.getId()).isEqualTo(e.getId());
        assertThat(d.getTitle()).isEqualTo("Analista de datos");
        assertThat(d.getOriginalSourceUrl()).isEqualTo("https://example.com/1");
        assertThat(d.getEmploymentType()).isEqualTo("Jornada completa");
        assertThat(d.getSourceName()).isEqualTo("portal");
        assertThat(d.getExternalReference()).isEqualTo("ext-1");
        assertThat(d.getStatus()).isEqualTo(JobStatus.REGISTERED);
        assertThat(d.getExtractedAt()).isEqualTo(NOW);
        assertThat(d.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void toEntityKeepsIdAndDoesNotCopyVersion() {
        UUID actor = UUID.randomUUID();
        JobOffer d = JobOffer.register(
                new NewJobOffer("Analista", "ACME", "D", "R", null, null, null,
                        "https://example.com/1", "portal", "ext-1", NOW),
                actor, NOW);

        JobOfferJpaEntity e = mapper.toEntity(d);

        assertThat(e.getId()).isEqualTo(d.getId());
        assertThat(e.getOriginalSourceUrl()).isEqualTo("https://example.com/1");
        assertThat(e.getStatus()).isEqualTo(JobStatus.REGISTERED);
        assertThat(e.getCreatedBy()).isEqualTo(actor);
        assertThat(e.getVersion()).isNull();   // null => Hibernate hace persist
    }

    @Test
    void copyToUpdatesManagedEntityWithoutTouchingIdOrVersion() {
        UUID actor = UUID.randomUUID();
        JobOffer d = JobOffer.register(
                new NewJobOffer("Analista", "ACME", "D", "R", null, null, null,
                        "https://example.com/1", "portal", "ext-1", NOW),
                actor, NOW);
        JobOfferJpaEntity e = mapper.toEntity(d);
        e.setVersion(7L);   // entidad ya gestionada por Hibernate

        d.updateContent(new JobOfferContent("Analista senior", "ACME", "D2", "R2",
                "Hibrido", "1500", "Media jornada", "https://example.com/2"),
                actor, NOW.plusSeconds(60));
        mapper.copyTo(e, d);

        assertThat(e.getId()).isEqualTo(d.getId());
        assertThat(e.getVersion()).isEqualTo(7L);
        assertThat(e.getTitle()).isEqualTo("Analista senior");
        assertThat(e.getSalary()).isEqualTo("1500");
        assertThat(e.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void toDomainToleratesNullOptionalFields() {
        var e = new JobOfferJpaEntity();
        e.setId(UUID.randomUUID());
        e.setTitle("T");
        e.setCompany("C");
        e.setDescription("D");
        e.setRequirements("R");
        e.setOriginalSourceUrl("https://example.com/1");
        e.setExtractedAt(NOW);
        e.setStatus(JobStatus.REGISTERED);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);

        JobOffer d = mapper.toDomain(e);

        assertThat(d.getLocation()).isNull();
        assertThat(d.getSalary()).isNull();
        assertThat(d.getSourceName()).isNull();
        assertThat(d.getExternalReference()).isNull();
    }
}
