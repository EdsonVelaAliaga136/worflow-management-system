package com.evela.sigotrab.adapters.web.dto;

import java.time.LocalDateTime;

import org.hibernate.validator.constraints.URL;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JobOfferForm(
                @NotBlank @Size(max = 200) String title,
                @NotBlank @Size(max = 150) String company,
                @NotBlank String description,
                @NotBlank String requirements,
                @Size(max = 150) String location,
                @Size(max = 100) String salary,
                @Size(max = 50) String employmentType,
                @NotBlank @Size(max = 500) @URL @Pattern(regexp = "^\\s*https?://\\S+\\s*$", message = "{offers.validation.url}") String originalSourceUrl,
                @Size(max = 60) String sourceName,
                @Size(max = 120) String externalReference,
                @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime extractedAt) {
}