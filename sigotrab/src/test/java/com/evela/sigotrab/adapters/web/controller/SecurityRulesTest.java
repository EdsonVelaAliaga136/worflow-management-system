package com.evela.sigotrab.adapters.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.evela.sigotrab.adapters.web.mapper.JobOfferWebMapper;
import com.evela.sigotrab.application.port.in.GetJobOffersUseCase;
import com.evela.sigotrab.application.port.in.RegisterJobOfferUseCase;
import com.evela.sigotrab.application.port.out.PageResult;
import com.evela.sigotrab.infrastructure.config.MessageConfig;
import com.evela.sigotrab.infrastructure.security.SecurityConfig;
import com.evela.sigotrab.infrastructure.security.SigAuthenticationFailureHandler;
import com.evela.sigotrab.infrastructure.security.SigUser;

@WebMvcTest(OffersController.class)
@Import({SecurityConfig.class, JobOfferWebMapper.class, MessageConfig.class})
class SecurityRulesTest {

    @Autowired MockMvc mvc;

    @MockitoBean RegisterJobOfferUseCase registerJobOffer;
    @MockitoBean GetJobOffersUseCase getJobOffers;
    @MockitoBean SigAuthenticationFailureHandler failureHandler;

    private void stubEmptyList() {
        when(getJobOffers.list(any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 20));
    }

    private static SigUser registrador() {
        return new SigUser(UUID.randomUUID(), "registrador", "x", true,
                List.of(new SimpleGrantedAuthority("ROLE_REGISTRADOR")));
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/offers"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "PUBLICADOR")
    void publicadorCannotOpenTheRegistry() throws Exception {
        mvc.perform(get("/offers/new"))
           .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void registradorCanOpenTheForm() throws Exception {
        mvc.perform(get("/offers/new"))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenTheForm() throws Exception {
        // /offers/new tiene matcher propio: si solo permite REGISTRADOR, este test falla.
        mvc.perform(get("/offers/new"))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenTheRegistry() throws Exception {
        stubEmptyList();

        mvc.perform(get("/offers"))
           .andExpect(status().isOk());
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/offers")
                .with(user(registrador()))
                .param("title", "T"))
           .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PUBLICADOR")
    void publicadorCannotPost() throws Exception {
        mvc.perform(post("/offers")
                .with(csrf())
                .param("title", "T"))
           .andExpect(status().isForbidden());
    }
}
