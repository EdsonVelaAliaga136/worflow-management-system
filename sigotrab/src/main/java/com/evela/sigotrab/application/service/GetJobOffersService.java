package com.evela.sigotrab.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evela.sigotrab.application.port.in.GetJobOffersUseCase;
import com.evela.sigotrab.application.port.out.JobOfferRepository;
import com.evela.sigotrab.application.port.out.PageQuery;
import com.evela.sigotrab.application.port.out.PageResult;
import com.evela.sigotrab.domain.exception.JobNotFoundException;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;



@Service 
@Transactional (readOnly = true)
public class GetJobOffersService implements GetJobOffersUseCase {

    private final JobOfferRepository offers;

    public GetJobOffersService(JobOfferRepository offers) {
        this.offers = offers;
    }

    @Override
    public JobOffer getById(UUID id) {
        return offers.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Override
    public PageResult<JobOffer> list(JobStatus status, PageQuery page) {
        return status == null ? offers.findAll(page) : offers.findByStatus(status, page);
    }
}
