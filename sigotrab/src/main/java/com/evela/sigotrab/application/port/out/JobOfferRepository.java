package com.evela.sigotrab.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;

public interface JobOfferRepository {
    void save(JobOffer jobOffer);
    Optional<JobOffer> findById(UUID id);
    PageResult<JobOffer> findAll(PageQuery pageQuery);
    PageResult<JobOffer> findByStatus(JobStatus status, PageQuery pageQuery);
    boolean existsBySourceReference(String sourceReference, String externalReference);
}
