package com.evela.sigotrab.application.port.in;

import com.evela.sigotrab.application.port.out.PageQuery;
import com.evela.sigotrab.application.port.out.PageResult;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;

import java.util.UUID;

public interface GetJobOffersUseCase {
    JobOffer getById(UUID id);
    PageResult<JobOffer> list(JobStatus statusOrNull, PageQuery page);
}
