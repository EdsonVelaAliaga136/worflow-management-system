package com.evela.sigotrab.adapters.web.controller;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.evela.sigotrab.adapters.web.dto.JobOfferEditForm;
import com.evela.sigotrab.adapters.web.dto.JobOfferForm;
import com.evela.sigotrab.adapters.web.mapper.JobOfferWebMapper;
import com.evela.sigotrab.application.port.in.GetJobOffersUseCase;
import com.evela.sigotrab.application.port.in.RegisterJobOfferUseCase;
import com.evela.sigotrab.application.port.in.UpdateJobOfferUseCase;
import com.evela.sigotrab.application.port.out.PageQuery;
import com.evela.sigotrab.application.port.out.PageResult;
import com.evela.sigotrab.domain.exception.DuplicateJobOfferException;
import com.evela.sigotrab.domain.exception.InvalidJobStateException;
import com.evela.sigotrab.domain.exception.JobNotFoundException;
import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;
import com.evela.sigotrab.infrastructure.security.SigUser;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller 
@RequestMapping("/offers")
@PreAuthorize("hasAnyRole('REGISTRADOR','ADMIN')")
public class OffersController {

    private static final int PAGE_SIZE = 20;

    private final RegisterJobOfferUseCase registerJobOffer;
    private final UpdateJobOfferUseCase updateJobOffer;
    private final GetJobOffersUseCase getJobOffers;
    private final JobOfferWebMapper mapper;
    private final Clock clock;


    public OffersController(RegisterJobOfferUseCase registerJobOffer, UpdateJobOfferUseCase updateJobOffer,
            GetJobOffersUseCase getJobOffers, JobOfferWebMapper mapper, Clock clock) {
        this.registerJobOffer = registerJobOffer;
        this.updateJobOffer = updateJobOffer;
        this.getJobOffers = getJobOffers;
        this.mapper = mapper;
        this.clock = clock;
    }

    @GetMapping 
    public String list(@RequestParam (defaultValue = "0") int page, Model model) {
        return renderList(null, page, model, false);
    }

    @GetMapping("/registered")
    public String registered(@RequestParam(defaultValue = "0") int page, Model model) {
        return renderList(JobStatus.REGISTERED, page, model, true);
    }

    private String renderList(JobStatus status, int page, Model model, boolean registered) {
        PageResult<JobOffer> result = getJobOffers.list(status, new PageQuery(page, PAGE_SIZE));
        model.addAttribute("offers", result.items().stream().map(mapper::toView).toList());
        model.addAttribute("page", result);
        model.addAttribute("totalElements", result.totalElements());
        model.addAttribute("registered", registered);
        model.addAttribute("baseUrl", registered ? "/offers/registered" : "/offers");
        model.addAttribute("activeNav", "offers");
        return "offers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new JobOfferForm(
                null, null, null, null, null, null, null, null, null, null,
                LocalDateTime.now(clock)));
        model.addAttribute("editing", false);
        model.addAttribute("activeNav", "new");
        return "offers/form";
    }

    @PostMapping 
    public String create(@Valid @ModelAttribute ("form") JobOfferForm form,
                         BindingResult binding,
                         @AuthenticationPrincipal SigUser user,
                         HttpServletRequest request,
                         RedirectAttributes ra) {    
        // BindingResult primero: si el formulario no es valido no hace falta el actor.
        if (binding.hasErrors()) {
            return "offers/form";
        }
        if (user == null) {
            return "redirect:/login";
        }
        ActorInfo actor = new ActorInfo(user.id(), user.getUsername(), request.getRemoteAddr());
        JobOffer offer;
        try {
            offer = registerJobOffer.register(mapper.toNewJobOffer(form), actor);
        } catch (DuplicateJobOfferException ex) {
            // Error global: el duplicado afecta a la pareja fuente + referencia.
            binding.reject("duplicate", ex.getMessage());
            return "offers/form";
        }
        ra.addFlashAttribute("success", true);
        return "redirect:/offers/" + offer.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("offer", mapper.toView(getJobOffers.getById(id)));
        model.addAttribute("activeNav", "offers");
        return "offers/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes ra) {
        JobOffer offer = getJobOffers.getById(id);
        if (!isEditable(offer)) {
            ra.addFlashAttribute("error", "offers.edit.notEditable");
            return "redirect:/offers/" + id;
        }
        model.addAttribute("form", mapper.toEditForm(offer));
        addEditModel(id, offer, model);
        return "offers/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("form") JobOfferEditForm form,
                         BindingResult binding,
                         @AuthenticationPrincipal SigUser user,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes ra) {
        // BindingResult primero: los errores de campo se repintan sin tocar la aplicacion.
        if (binding.hasErrors()) {
            addEditModel(id, getJobOffers.getById(id), model);
            return "offers/form";
        }
        if (user == null) {
            return "redirect:/login";
        }
        ActorInfo actor = new ActorInfo(user.id(), user.getUsername(), request.getRemoteAddr());
        try {
            updateJobOffer.update(id, mapper.toContent(form), actor);
        } catch (InvalidJobStateException ex) {
            // Carrera: otro usuario la aprobo/rechazo entre el GET y el POST.
            ra.addFlashAttribute("error", "offers.edit.notEditable");
            return "redirect:/offers/" + id;
        } catch (ObjectOptimisticLockingFailureException ex) {
            // Dos ediciones simultaneas: @Version detecto el choque.
            ra.addFlashAttribute("error", "offers.edit.conflict");
            return "redirect:/offers/" + id;
        }
        ra.addFlashAttribute("updated", true);
        return "redirect:/offers/" + id;
    }

    private static boolean isEditable(JobOffer offer) {
        return offer.getStatus() == JobStatus.REGISTERED;
    }

    private void addEditModel(UUID id, JobOffer offer, Model model) {
        model.addAttribute("offer", mapper.toView(offer));
        model.addAttribute("offerId", id);
        model.addAttribute("editing", true);
        model.addAttribute("activeNav", "offers");
    }

    @ExceptionHandler (JobNotFoundException.class)
    @ResponseStatus (HttpStatus.NOT_FOUND)
    public String notFound() {
        return "offers/not-found";
    }
}

