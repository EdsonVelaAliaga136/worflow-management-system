package com.evela.sigotrab.adapters.web.dto;

import java.util.UUID;

import com.evela.sigotrab.domain.model.JobStatus;

public record JobOfferView(
        UUID id,
        String title,
        String company,
        String description,
        String requirements,
        String location,
        String salary,
        String employmentType,
        String originalSourceUrl,
        String sourceName,
        String externalReference,
        String extractedAt,
        String publicationUrl,
        JobStatus status,
        String statusLabel,
        String createdAt,
        String updatedAt
) {
}

