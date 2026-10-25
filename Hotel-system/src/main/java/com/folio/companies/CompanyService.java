package com.folio.companies;

import java.util.List;

public interface CompanyService {

    CompanyResponse create(CompanyRequest request);

    CompanyResponse getById(Long id);

    CompanyResponse getByEmail(String email);

    List<CompanyResponse> getByCountry(String country);

    List<CompanyResponse> getByCity(String city);

    List<CompanyResponse> getByStatus(CompanyStatus status);

    CompanyResponse updateStatus(Long id, CompanyStatus status);

    /** Moves a PENDING_VERIFICATION application to ACTIVE, grants the submitter
     *  HOTEL_MANAGER, and creates their OWNER CompanyUser row if one doesn't exist yet. */
    CompanyResponse approve(Long id);

    /** Moves a PENDING_VERIFICATION application to REJECTED with a reason — no role or
     *  membership side effects. */
    CompanyResponse reject(Long id, String reason);

    CompanyResponse update(Long id, CompanyRequest request);

    void delete(Long id);
}
