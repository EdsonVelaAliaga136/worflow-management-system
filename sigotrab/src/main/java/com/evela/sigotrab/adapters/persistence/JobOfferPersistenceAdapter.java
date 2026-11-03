package com.evela.sigotrab.adapters.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.evela.sigotrab.adapters.persistence.entity.JobOfferJpaEntity;
import com.evela.sigotrab.adapters.persistence.mapper.JobOfferEntityMapper;
import com.evela.sigotrab.adapters.persistence.repository.JobOfferJpaRepository;
import com.evela.sigotrab.application.port.out.JobOfferRepository;
import com.evela.sigotrab.application.port.out.PageQuery;
import com.evela.sigotrab.application.port.out.PageResult;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;

@Repository 
public class JobOfferPersistenceAdapter implements JobOfferRepository {

    private final JobOfferJpaRepository jpa;
    private final JobOfferEntityMapper mapper;

    public JobOfferPersistenceAdapter(JobOfferJpaRepository jpa, JobOfferEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public void save(JobOffer jobOffer) {
        jpa.save(mapper.toEntity(jobOffer));
    }

    @Override
    public Optional<JobOffer> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<JobOffer> findAll(PageQuery page) {
        return toPageResult(jpa.findAll(pageable(page)), page);
    }

    @Override
    public PageResult<JobOffer> findByStatus(JobStatus status, PageQuery page) {
        return toPageResult(jpa.findByStatus(status, pageable(page)), page);
    }

    @Override
    public boolean existsBySourceReference(String sourceName, String externalReference) {
        return jpa.existsBySourceNameAndExternalReference(sourceName, externalReference);
    }

    private static Pageable pageable(PageQuery q) {
        return PageRequest.of(q.page(), q.size(), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private PageResult<JobOffer> toPageResult(Page<JobOfferJpaEntity> p, PageQuery q) {
        return new PageResult<>(p.getContent().stream().map(mapper::toDomain).toList(),
                p.getTotalElements(), q.page(), q.size());
    }
}
