package com.evela.sigotrab.adapters.persistence.repository;

import java.util.UUID;



import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.evela.sigotrab.adapters.persistence.entity.JobOfferJpaEntity;
import com.evela.sigotrab.domain.model.JobStatus;

public interface JobOfferJpaRepository extends JpaRepository<JobOfferJpaEntity, UUID> {
    Page<JobOfferJpaEntity> findByStatus(JobStatus status, Pageable pageable);
    boolean existsBySourceNameAndExternalReference(String sourceName, String externalReference);
}
