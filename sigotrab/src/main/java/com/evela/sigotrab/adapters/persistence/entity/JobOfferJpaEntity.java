package com.evela.sigotrab.adapters.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.evela.sigotrab.domain.model.JobStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table (name = "job_offer")
@Getter 
@Setter 
@NoArgsConstructor 
public class JobOfferJpaEntity {

    @Id 
    private UUID id;                       // sin @GeneratedValue: el id lo genera el dominio

    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 150) private String company;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(nullable = false, columnDefinition = "text") private String requirements;
    @Column(length = 150) private String location;
    @Column(length = 100) private String salary;
    @Column(name = "employment_type", length = 50) private String employmentType;
    @Column(name = "source_url", nullable = false, length = 500) private String originalSourceUrl;
    @Column(name = "source_name", length = 60) private String sourceName;
    @Column(name = "external_reference", length = 120) private String externalReference;
    @Column(name = "extracted_at", nullable = false) private Instant extractedAt;
    @Column(name = "publication_url", length = 500) private String publicationUrl;

    @Enumerated (EnumType.STRING)
    @Column(nullable = false, length = 20) private JobStatus status;

    @Column (name = "assigned_user_id") private UUID assignedUserId;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "lock_expires_at") private Instant lockExpiresAt;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "published_at") private Instant publishedAt;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;

    @Version 
    private Long version;                  // wrapper: null ⇒ entidad nueva ⇒ persist
}
