package com.evela.sigotrab.adapters.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import com.evela.sigotrab.adapters.web.dto.JobOfferEditForm;
import com.evela.sigotrab.adapters.web.mapper.JobOfferWebMapper;
import com.evela.sigotrab.application.port.in.GetJobOffersUseCase;
import com.evela.sigotrab.application.port.in.RegisterJobOfferUseCase;
import com.evela.sigotrab.application.port.in.UpdateJobOfferUseCase;
import com.evela.sigotrab.application.port.out.PageResult;
import com.evela.sigotrab.domain.exception.DuplicateJobOfferException;
import com.evela.sigotrab.domain.exception.InvalidJobStateException;
import com.evela.sigotrab.domain.exception.JobNotFoundException;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;
import com.evela.sigotrab.domain.model.NewJobOffer;
import com.evela.sigotrab.infrastructure.config.MessageConfig;
import com.evela.sigotrab.infrastructure.security.SecurityConfig;
import com.evela.sigotrab.infrastructure.security.SigAuthenticationFailureHandler;
import com.evela.sigotrab.infrastructure.security.SigUser;

@WebMvcTest(OffersController.class)
@Import({SecurityConfig.class, JobOfferWebMapper.class, MessageConfig.class})
class OffersControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired MockMvc mvc;

    @MockitoBean RegisterJobOfferUseCase registerJobOffer;
    @MockitoBean UpdateJobOfferUseCase updateJobOffer;
    @MockitoBean GetJobOffersUseCase getJobOffers;
    @MockitoBean SigAuthenticationFailureHandler failureHandler;

    private static SigUser registrador() {
        return new SigUser(UUID.randomUUID(), "registrador", "x", true,
                List.of(new SimpleGrantedAuthority("ROLE_REGISTRADOR")));
    }

    private static JobOffer offer() {
        return JobOffer.register(
                new NewJobOffer("Analista", "ACME", "D", "R", null, null, null,
                        "https://example.com/1", "portal", "ext-1", NOW),
                UUID.randomUUID(), NOW);
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void newFormRenders() throws Exception {
        mvc.perform(get("/offers/new"))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/form"))
           .andExpect(model().attributeExists("form"))
           .andExpect(model().attribute("editing", false))
           // en el alta la procedencia SI se captura
           .andExpect(content().string(containsString("name=\"sourceName\"")))
           .andExpect(content().string(containsString("name=\"extractedAt\"")));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void listRendersOffersAndCount() throws Exception {
        when(getJobOffers.list(isNull(), any()))
                .thenReturn(new PageResult<>(List.of(offer()), 1, 0, 20));

        mvc.perform(get("/offers"))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/list"))
           .andExpect(model().attribute("totalElements", 1L))
           .andExpect(model().attribute("registered", false));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void registeredFiltersByRegisteredStatus() throws Exception {
        when(getJobOffers.list(eq(JobStatus.REGISTERED), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 20));

        mvc.perform(get("/offers/registered"))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/list"))
           .andExpect(model().attribute("registered", true));

        verify(getJobOffers).list(eq(JobStatus.REGISTERED), any());
    }

    @Test
    void createRedirectsToDetailOnSuccess() throws Exception {
        SigUser user = registrador();
        JobOffer created = offer();
        when(registerJobOffer.register(any(), any())).thenReturn(created);

        mvc.perform(post("/offers")
                .with(user(user))
                .with(csrf())
                .param("title", "Analista")
                .param("company", "ACME")
                .param("description", "D")
                .param("requirements", "R")
                .param("originalSourceUrl", "https://example.com/1")
                .param("extractedAt", "2026-01-15T10:00"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/offers/" + created.getId()))
           .andExpect(flash().attribute("success", true));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void createShowsFieldErrorsWhenRequiredDataIsMissing() throws Exception {
        mvc.perform(post("/offers").with(csrf()))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/form"))
           .andExpect(model().attributeHasFieldErrors("form",
                   "title", "company", "description", "requirements",
                   "originalSourceUrl", "extractedAt"));

        verifyNoInteractions(registerJobOffer);
    }

    @Test
    void createShowsGlobalErrorWhenDuplicate() throws Exception {
        when(registerJobOffer.register(any(), any()))
                .thenThrow(new DuplicateJobOfferException("portal", "ext-1"));

        mvc.perform(post("/offers")
                .with(user(registrador()))
                .with(csrf())
                .param("title", "Analista")
                .param("company", "ACME")
                .param("description", "D")
                .param("requirements", "R")
                .param("originalSourceUrl", "https://example.com/1")
                .param("sourceName", "portal")
                .param("externalReference", "ext-1")
                .param("extractedAt", "2026-01-15T10:00"))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/form"))
           .andExpect(model().attributeHasErrors("form"))
           .andExpect(result -> {
               var binding = (BindingResult) result.getModelAndView().getModel()
                       .get(BindingResult.MODEL_KEY_PREFIX + "form");
               assertThat(binding.getGlobalErrors())
                       .extracting(ObjectError::getDefaultMessage)
                       .anyMatch(m -> m.contains("portal"));
           });
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void detailRendersOffer() throws Exception {
        JobOffer offer = offer();
        when(getJobOffers.getById(offer.getId())).thenReturn(offer);

        mvc.perform(get("/offers/{id}", offer.getId()))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/detail"))
           .andExpect(model().attributeExists("offer"));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void detailReturns404WhenOfferDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(getJobOffers.getById(id)).thenThrow(new JobNotFoundException(id));

        mvc.perform(get("/offers/{id}", id))
           .andExpect(status().isNotFound())
           .andExpect(view().name("offers/not-found"));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void editFormIsPrefilledWithCurrentValues() throws Exception {
        JobOffer offer = offer();
        when(getJobOffers.getById(offer.getId())).thenReturn(offer);

        mvc.perform(get("/offers/{id}/edit", offer.getId()))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/form"))
           .andExpect(model().attribute("editing", true))
           .andExpect(model().attribute("offerId", offer.getId()))
           .andExpect(model().attribute("form", instanceOf(JobOfferEditForm.class)))
           .andExpect(result -> {
               var form = (JobOfferEditForm) result.getModelAndView().getModel().get("form");
               assertThat(form.title()).isEqualTo("Analista");
               assertThat(form.originalSourceUrl()).isEqualTo("https://example.com/1");
           })
           // en la edicion la procedencia es de solo lectura: no hay inputs
           .andExpect(content().string(not(containsString("name=\"sourceName\""))))
           .andExpect(content().string(not(containsString("name=\"extractedAt\""))));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void editFormRedirectsWhenOfferIsNotRegistered() throws Exception {
        JobOffer approved = offer();
        approved.approve(UUID.randomUUID(), NOW);
        when(getJobOffers.getById(approved.getId())).thenReturn(approved);

        mvc.perform(get("/offers/{id}/edit", approved.getId()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/offers/" + approved.getId()))
           .andExpect(flash().attribute("error", "offers.edit.notEditable"));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void editFormReturns404WhenOfferDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(getJobOffers.getById(id)).thenThrow(new JobNotFoundException(id));

        mvc.perform(get("/offers/{id}/edit", id))
           .andExpect(status().isNotFound())
           .andExpect(view().name("offers/not-found"));
    }

    @Test
    void updateRedirectsToDetailOnSuccess() throws Exception {
        JobOffer offer = offer();
        when(updateJobOffer.update(eq(offer.getId()), any(), any())).thenReturn(offer);

        mvc.perform(post("/offers/{id}/edit", offer.getId())
                .with(user(registrador()))
                .with(csrf())
                .param("title", "Analista senior")
                .param("company", "ACME")
                .param("description", "D")
                .param("requirements", "R")
                .param("originalSourceUrl", "https://example.com/1"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/offers/" + offer.getId()))
           .andExpect(flash().attribute("updated", true));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void updateShowsFieldErrorsWhenRequiredDataIsMissing() throws Exception {
        JobOffer offer = offer();
        when(getJobOffers.getById(offer.getId())).thenReturn(offer);

        mvc.perform(post("/offers/{id}/edit", offer.getId()).with(csrf()))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/form"))
           .andExpect(model().attribute("editing", true))
           .andExpect(model().attributeHasFieldErrors("form",
                   "title", "company", "description", "requirements", "originalSourceUrl"));

        verifyNoInteractions(updateJobOffer);
    }

    @Test
    void updateRedirectsWhenOfferIsNoLongerRegistered() throws Exception {
        JobOffer offer = offer();
        when(updateJobOffer.update(any(), any(), any()))
                .thenThrow(new InvalidJobStateException(offer.getId(), JobStatus.APPROVED, "update"));

        mvc.perform(post("/offers/{id}/edit", offer.getId())
                .with(user(registrador()))
                .with(csrf())
                .param("title", "T")
                .param("company", "C")
                .param("description", "D")
                .param("requirements", "R")
                .param("originalSourceUrl", "https://example.com/1"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/offers/" + offer.getId()))
           .andExpect(flash().attribute("error", "offers.edit.notEditable"));
    }

    @Test
    void updateRedirectsWhenAnotherUserChangedTheOffer() throws Exception {
        JobOffer offer = offer();
        when(updateJobOffer.update(any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(JobOffer.class, offer.getId()));

        mvc.perform(post("/offers/{id}/edit", offer.getId())
                .with(user(registrador()))
                .with(csrf())
                .param("title", "T")
                .param("company", "C")
                .param("description", "D")
                .param("requirements", "R")
                .param("originalSourceUrl", "https://example.com/1"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/offers/" + offer.getId()))
           .andExpect(flash().attribute("error", "offers.edit.conflict"));
    }
}
