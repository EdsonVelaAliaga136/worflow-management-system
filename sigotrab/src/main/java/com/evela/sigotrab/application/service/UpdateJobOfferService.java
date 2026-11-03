package com.evela.sigotrab.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.evela.sigotrab.application.port.in.UpdateJobOfferUseCase;
import com.evela.sigotrab.application.port.out.AuditPort;
import com.evela.sigotrab.application.port.out.JobOfferRepository;
import com.evela.sigotrab.domain.exception.JobNotFoundException;
import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.AuditAction;
import com.evela.sigotrab.domain.model.AuditRecord;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobOfferContent;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class UpdateJobOfferService implements UpdateJobOfferUseCase {

    private final JobOfferRepository offers;
    private final AuditPort audit;
    private final Clock clock;

    public UpdateJobOfferService(JobOfferRepository offers, AuditPort audit, Clock clock) {
        this.offers = offers;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public JobOffer update(UUID id, JobOfferContent content, ActorInfo actor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(actor, "actor");
        Instant now = clock.instant();

        JobOffer offer = offers.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        Map<String, Object> oldValues = snapshot(offer);

        // El dominio decide: fuera de REGISTERED lanza InvalidJobStateException.
        offer.updateContent(content, actor.userId(), now);

        offers.save(offer);
        audit.record(AuditRecord.success(
                AuditAction.UPDATE, "JOB_OFFER", offer.getId().toString(), actor,
                oldValues, snapshot(offer)));
        return offer;
    }

    private static Map<String, Object> snapshot(JobOffer offer) {
        var values = new LinkedHashMap<String, Object>();
        values.put("title", offer.getTitle());
        values.put("company", offer.getCompany());
        values.put("description", offer.getDescription());
        values.put("requirements", offer.getRequirements());
        values.put("location", offer.getLocation());
        values.put("salary", offer.getSalary());
        values.put("employmentType", offer.getEmploymentType());
        values.put("originalSourceUrl", offer.getOriginalSourceUrl());
        return values;
    }
}
