package com.evela.sigotrab.adapters.web.mapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.evela.sigotrab.adapters.web.dto.JobOfferForm;
import com.evela.sigotrab.adapters.web.dto.JobOfferView;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;
import com.evela.sigotrab.domain.model.NewJobOffer;

@Component 
public class JobOfferWebMapper {

    public NewJobOffer toNewJobOffer(JobOfferForm f) {
        return new NewJobOffer(
                f.title().trim(),
                f.company().trim(),
                f.description(),
                f.requirements(),
                trimToNull(f.location()),
                trimToNull(f.salary()),
                trimToNull(f.employmentType()),
                f.originalSourceUrl().trim(),
                trimToNull(f.sourceName()),
                trimToNull(f.externalReference()),
                f.extractedAt().toInstant(ZoneOffset.UTC));
    }

    public JobOfferView toView(JobOffer o) {
        return new JobOfferView(
                o.getId(), o.getTitle(), o.getCompany(), o.getDescription(), o.getRequirements(),
                o.getLocation(), o.getSalary(), o.getEmploymentType(), o.getOriginalSourceUrl(),
                o.getSourceName(), o.getExternalReference(), fmt(o.getExtractedAt()),
                o.getPublicationUrl(), o.getStatus(), label(o.getStatus()),
                fmt(o.getCreatedAt()), fmt(o.getUpdatedAt()));
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private static String fmt(Instant i) { return i == null ? null : FMT.format(i); }

    private static String label(JobStatus s) {
        return switch (s) {
            case REGISTERED -> "Registrada";
            case APPROVED -> "Aprobada";
            case IN_PROGRESS -> "En curso";
            case PUBLISHED -> "Publicada";
            case REJECTED -> "Rechazada";
        };
    }
}

