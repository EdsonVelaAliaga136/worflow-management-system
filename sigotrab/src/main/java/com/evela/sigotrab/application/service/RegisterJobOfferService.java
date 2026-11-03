package com.evela.sigotrab.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.evela.sigotrab.application.port.in.RegisterJobOfferUseCase;
import com.evela.sigotrab.application.port.out.AuditPort;
import com.evela.sigotrab.application.port.out.JobOfferRepository;
import com.evela.sigotrab.domain.exception.DuplicateJobOfferException;
import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.AuditAction;
import com.evela.sigotrab.domain.model.AuditRecord;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.NewJobOffer;

import jakarta.transaction.Transactional;

@Service 
@Transactional 
public class RegisterJobOfferService implements RegisterJobOfferUseCase {
    private final JobOfferRepository offers;
    private final AuditPort audit;
    private final Clock clock;

    public RegisterJobOfferService(JobOfferRepository offers, AuditPort audit, Clock clock) {
        this.offers = offers;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public JobOffer register(NewJobOffer data, ActorInfo actor) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(actor, "actor");
        Instant now = clock.instant();

        if (hasSourceReference(data)
                && offers.existsBySourceReference(data.sourceName(), data.externalReference())) {
            throw new DuplicateJobOfferException(data.sourceName(), data.externalReference());
        }

        JobOffer offer = JobOffer.register(data, actor.userId(), now);
        offers.save(offer);
        audit.record(AuditRecord.success(
                AuditAction.CREATE, "JOB_OFFER", offer.getId().toString(), actor, snapshot(offer)));
        return offer;
    }

    private static boolean hasSourceReference(NewJobOffer data) {
        return data.sourceName() != null && !data.sourceName().isBlank()
                && data.externalReference() != null && !data.externalReference().isBlank();
    }

    private static Map<String, Object> snapshot(JobOffer offer) {
        var values = new LinkedHashMap<String, Object>();
        values.put("title", offer.getTitle());
        values.put("company", offer.getCompany());
        values.put("status", offer.getStatus().name());
        values.put("sourceName", offer.getSourceName());
        values.put("externalReference", offer.getExternalReference());
        return values;
    }
}
