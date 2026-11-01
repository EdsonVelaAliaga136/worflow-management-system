package com.evela.sigotrab.domain.model;

public record JobOfferContent(
    String title, 
    String company,
    String description,
    String requirements,
    String location,
    String salary,
    String employmentType,
    String originalSourceUrl
){
}
