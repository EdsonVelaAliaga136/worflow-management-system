package com.evela.sigotrab.application.port.in;

import java.util.UUID;

import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobOfferContent;

public interface UpdateJobOfferUseCase {
    JobOffer update(UUID id, JobOfferContent content, ActorInfo actor);
}
