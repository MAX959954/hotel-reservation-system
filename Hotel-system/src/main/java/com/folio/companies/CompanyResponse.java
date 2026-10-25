package com.folio.companies;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CompanyResponse {

    private Long id;
    private String name;
    private String legalName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
    private String webSite;
    private String logoUrl;
    private Long submittedByUserId;
    private String bankAccountHolder;
    private String bankIban;
    private String rejectionReason;
    private CompanyStatus status;
    // JSON keys predate the camelCase field names; kept so existing API clients don't break.
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

}
