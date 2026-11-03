package com.evela.sigotrab.adapters.web.dto;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de una oferta. La procedencia (fuente, referencia externa y fecha de
 * extracción) no se edita: identifica la oferta y sostiene la deduplicación.
 */
public record JobOfferEditForm(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 150) String company,
        @NotBlank String description,
        @NotBlank String requirements,
        @Size(max = 150) String location,
        @Size(max = 100) String salary,
        @Size(max = 50) String employmentType,
        @NotBlank @Size(max = 500) @URL
        @Pattern(regexp = "^\\s*https?://\\S+\\s*$", message = "{offers.validation.url}")
        String originalSourceUrl
) {
}
