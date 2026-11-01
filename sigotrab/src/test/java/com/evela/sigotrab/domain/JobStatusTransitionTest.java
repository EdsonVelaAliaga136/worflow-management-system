package com.evela.sigotrab.domain;

import com.evela.sigotrab.domain.model.JobStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobStatusTransitionTest {

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "REGISTERED,  APPROVED,    true",
            "REGISTERED,  REJECTED,    true",
            "REGISTERED,  IN_PROGRESS, false",
            "REGISTERED,  PUBLISHED,   false",
            "APPROVED,    IN_PROGRESS, true",
            "APPROVED,    PUBLISHED,   false",
            "APPROVED,    REJECTED,    false",
            "IN_PROGRESS, PUBLISHED,   true",
            "IN_PROGRESS, APPROVED,    true",
            "IN_PROGRESS, REGISTERED,  false",
            "PUBLISHED,   APPROVED,    false",
            "PUBLISHED,   IN_PROGRESS, false",
            "REJECTED,    APPROVED,    false",
            "REJECTED,    IN_PROGRESS, false"
    })
    void onlyConfiguredTransitionsAreAllowed(JobStatus from, JobStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void terminalStatesHaveNoOutgoingTransitions(JobStatus target) {
        assertThat(JobStatus.PUBLISHED.canTransitionTo(target)).isFalse();
        assertThat(JobStatus.REJECTED.canTransitionTo(target)).isFalse();
    }

}
