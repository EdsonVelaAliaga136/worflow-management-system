package com.evela.sigotrab.application.port.in;

import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.NewJobOffer;

public interface RegisterJobOfferUseCase {
    JobOffer register(NewJobOffer data, ActorInfo actor);
}
