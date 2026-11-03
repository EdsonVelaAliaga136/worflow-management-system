package com.evela.sigotrab.adapters.persistence.mapper;

import org.springframework.stereotype.Component;

import com.evela.sigotrab.adapters.persistence.entity.JobOfferJpaEntity;
import com.evela.sigotrab.domain.model.JobOffer;

@Component 
public class JobOfferEntityMapper {

    public JobOffer toDomain(JobOfferJpaEntity e) {
        return JobOffer.reconstitute(
                e.getId(), e.getTitle(), e.getCompany(), e.getDescription(), e.getRequirements(),
                e.getLocation(), e.getSalary(), e.getEmploymentType(), e.getOriginalSourceUrl(),
                e.getSourceName(), e.getExternalReference(), e.getExtractedAt(), e.getPublicationUrl(),
                e.getStatus(), e.getAssignedUserId(), e.getLockedAt(), e.getLockExpiresAt(),
                e.getApprovedAt(), e.getPublishedAt(), e.getCreatedAt(), e.getCreatedBy(),
                e.getUpdatedAt(), e.getUpdatedBy());
    }

    public JobOfferJpaEntity toEntity(JobOffer d) {
        var e = new JobOfferJpaEntity();
        e.setId(d.getId());
        e.setTitle(d.getTitle());
        e.setCompany(d.getCompany());
        e.setDescription(d.getDescription());
        e.setRequirements(d.getRequirements());
        e.setLocation(d.getLocation());
        e.setSalary(d.getSalary());
        e.setEmploymentType(d.getEmploymentType());
        e.setOriginalSourceUrl(d.getOriginalSourceUrl());
        e.setSourceName(d.getSourceName());
        e.setExternalReference(d.getExternalReference());
        e.setExtractedAt(d.getExtractedAt());
        e.setPublicationUrl(d.getPublicationUrl());
        e.setStatus(d.getStatus());
        e.setAssignedUserId(d.getAssignedUserId());
        e.setLockedAt(d.getLockedAt());
        e.setLockExpiresAt(d.getLockExpiresAt());
        e.setApprovedAt(d.getApprovedAt());
        e.setPublishedAt(d.getPublishedAt());
        e.setCreatedAt(d.getCreatedAt());
        e.setCreatedBy(d.getCreatedBy());
        e.setUpdatedAt(d.getUpdatedAt());
        e.setUpdatedBy(d.getUpdatedBy());
        return e;
    }
}
