package com.evela.sigotrab.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.evela.sigotrab.domain.exception.InvalidJobStateException;
import com.evela.sigotrab.domain.exception.InvalidTransitionException;
import com.evela.sigotrab.domain.exception.RejectionReasonRequiredException;
import com.evela.sigotrab.domain.exception.UnauthorizedJobOperationException;

public class JobOffer {
    private final UUID id;
    private String title;
    private String company;
    private String description;
    private String requirements;
    private String location;
    private String salary;
    private String employmentType;
    private String originalSourceUrl;
    private final String sourceName;
    private final String externalReference;
    private final Instant extractedAt;
    private String publicationUrl;
    private JobStatus status;
    private UUID assignedUserId;
    private Instant lockedAt;
    private Instant lockExpiresAt;
    private Instant approvedAt;
    private Instant publishedAt;
    private final Instant createdAt;
    private final UUID createdBy;
    private Instant updatedAt;
    private UUID updatedBy;

    
    // Constructor privado exclusivo para reconstruir desde la base de datos (Mapper / JPA)
    private JobOffer(
            UUID id,
            String title, String company, String description, String requirements,
            String location, String salary, String employmentType,
            String originalSourceUrl, String sourceName, String externalReference,
            Instant extractedAt, String publicationUrl,
            JobStatus status, UUID assignedUserId, Instant lockedAt, Instant lockExpiresAt,
            Instant approvedAt, Instant publishedAt,
            Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy
    ) {
        this.id = id;
        this.title = title;
        this.company = company;
        this.description = description;
        this.requirements = requirements;
        this.location = location;
        this.salary = salary;
        this.employmentType = employmentType;
        this.originalSourceUrl = originalSourceUrl;
        this.sourceName = sourceName;
        this.externalReference = externalReference;
        this.extractedAt = extractedAt;
        this.publicationUrl = publicationUrl;
        this.status = status;
        this.assignedUserId = assignedUserId;
        this.lockedAt = lockedAt;
        this.lockExpiresAt = lockExpiresAt;
        this.approvedAt = approvedAt;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    private JobOffer(UUID id, NewJobOffer data, UUID actor, Instant now) {
        this.id = id;
        this.sourceName = data.sourceName();
        this.externalReference = data.externalReference();
        this.extractedAt = data.extractedAt();
        this.createdAt = now;
        this.createdBy = actor;

        this.title = data.title();
        this.company = data.company();
        this.description = data.description();
        this.requirements = data.requirements();
        this.location = data.location();
        this.salary = data.salary();
        this.employmentType = data.employmentType();
        this.originalSourceUrl = data.originalSourceUrl();

        this.status = JobStatus.REGISTERED;
        this.updatedAt = now;
        this.updatedBy = actor;

        this.approvedAt = null;
        this.publishedAt = null;
        this.assignedUserId = null;
        this.lockedAt = null;
        this.lockExpiresAt = null;
        this.publicationUrl = null;
    }

    public static JobOffer register(NewJobOffer data, UUID actor, Instant now) {
        Objects.requireNonNull(data, "NewJobOffer data cannot be null");
        Objects.requireNonNull(actor, "Actor UUID cannot be null");
        Objects.requireNonNull(now, "Instant now cannot be null");

        validateRequiredText(data.title(), "title");
        validateRequiredText(data.company(), "company");
        validateRequiredText(data.description(), "description");
        validateRequiredText(data.requirements(), "requirements");
        validateRequiredText(data.originalSourceUrl(), "originalSourceUrl");
        Objects.requireNonNull(data.extractedAt(), "extractedAt cannot be null");

        return new JobOffer(UUID.randomUUID(), data, actor, now);
    }

    public void updateContent(JobOfferContent content, UUID actor, Instant now) {
        Objects.requireNonNull(content, "JobOfferContent cannot be null");
        Objects.requireNonNull(actor, "Actor UUID cannot be null");
        Objects.requireNonNull(now, "Instant now cannot be null");

        if (this.status != JobStatus.REGISTERED) {
            throw new InvalidJobStateException(this.id, this.status, "update");
        }

        validateRequiredText(content.title(), "title");
        validateRequiredText(content.company(), "company");
        validateRequiredText(content.description(), "description");
        validateRequiredText(content.requirements(), "requirements");
        validateRequiredText(content.originalSourceUrl(), "originalSourceUrl");

        this.title = content.title();
        this.company = content.company();
        this.description = content.description();
        this.requirements = content.requirements();
        this.location = content.location();
        this.salary = content.salary();
        this.employmentType = content.employmentType();
        this.originalSourceUrl = content.originalSourceUrl();

        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void approve(UUID actor, Instant now) {
        Objects.requireNonNull(actor, "Actor UUID cannot be null");
        Objects.requireNonNull(now, "Instant now cannot be null");

        transitionTo(JobStatus.APPROVED, actor, now);
        this.approvedAt = now;
    }

    public void reject(UUID actor, String reason, Instant now) {
        Objects.requireNonNull(actor, "Actor UUID cannot be null");
        Objects.requireNonNull(now, "Instant now cannot be null");

        if (reason == null || reason.isBlank()) {
            throw new RejectionReasonRequiredException(this.id);
        }

        transitionTo(JobStatus.REJECTED, actor, now);
    }

    public void acquire(UUID userId, Duration lockDuration, Instant now) {
        Objects.requireNonNull(userId, "UserId cannot be null");
        Objects.requireNonNull(lockDuration, "LockDuration cannot be null");
        Objects.requireNonNull(now, "Instant now cannot be null");

        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("Lock duration must be positive");
        }

        transitionTo(JobStatus.IN_PROGRESS, userId, now);
        this.assignedUserId = userId;
        this.lockedAt = now;
        this.lockExpiresAt = now.plus(lockDuration);
    }

    public void release(UUID actor, String reason, Instant now) {
        Objects.requireNonNull(actor, "Actor UUID cannot be null");
        Objects.requireNonNull(now, "Instant now cannot be null");

        transitionTo(JobStatus.APPROVED, actor, now);
        clearLock();
    }

    public void publish(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "UserId cannot be null");
        Objects.requireNonNull(now, "Instant now cannot be null");

        if (!isAssignedTo(userId)) {
            throw new UnauthorizedJobOperationException(this.id, userId);
        }

        transitionTo(JobStatus.PUBLISHED, userId, now);
        this.publishedAt = now;
        clearLock();
    }

    private void transitionTo(JobStatus target, UUID actor, Instant now) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidTransitionException(this.id, this.status, target);
        }
        this.status = target;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    private void clearLock() {
        this.assignedUserId = null;
        this.lockedAt = null;
        this.lockExpiresAt = null;
    }

    private static void validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field '" + fieldName + "' is required and cannot be blank");
        }
    }

    public boolean isLockExpired(Instant now) {
        Objects.requireNonNull(now, "Instant now cannot be null");
        return this.status == JobStatus.IN_PROGRESS
                && this.lockExpiresAt != null
                && this.lockExpiresAt.isBefore(now);
    }

    public static JobOffer reconstitute(
            UUID id,
            String title, String company, String description, String requirements,
            String location, String salary, String employmentType,
            String originalSourceUrl, String sourceName, String externalReference,
            Instant extractedAt, String publicationUrl,
            JobStatus status, UUID assignedUserId, Instant lockedAt, Instant lockExpiresAt,
            Instant approvedAt, Instant publishedAt,
            Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {

        return new JobOffer(
                id, title, company, description, requirements, location, salary,
                employmentType, originalSourceUrl, sourceName, externalReference, extractedAt,
                publicationUrl, status, assignedUserId, lockedAt, lockExpiresAt, approvedAt,
                publishedAt, createdAt, createdBy, updatedAt, updatedBy
        );
    }

    public boolean isAssignedTo(UUID userId) {
        return userId != null && userId.equals(this.assignedUserId);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getDescription() {
        return description;
    }

    public String getRequirements() {
        return requirements;
    }

    public String getLocation() {
        return location;
    }

    public String getSalary() {
        return salary;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public String getOriginalSourceUrl() {
        return originalSourceUrl;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Instant getExtractedAt() {
        return extractedAt;
    }

    public String getPublicationUrl() {
        return publicationUrl;
    }

    public JobStatus getStatus() {
        return status;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getLockExpiresAt() {
        return lockExpiresAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

}
