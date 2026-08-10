package companies;

import java.util.List;

public interface CompaniesService {

    CompaniesResponse create(CompaniesRequest request);

    CompaniesResponse getById(Long id);

    CompaniesResponse getByEmail(String email);

    List<CompaniesResponse> getByCountry(String country);

    List<CompaniesResponse> getByCity(String city);

    List<CompaniesResponse> getByStatus(CompaniesStatus status);

    CompaniesResponse updateStatus(Long id, CompaniesStatus status);

    /** Moves a PENDING_VERIFICATION application to ACTIVE, grants the submitter
     *  HOTEL_MANAGER, and creates their OWNER CompanyUser row if one doesn't exist yet. */
    CompaniesResponse approve(Long id);

    /** Moves a PENDING_VERIFICATION application to REJECTED with a reason — no role or
     *  membership side effects. */
    CompaniesResponse reject(Long id, String reason);

    CompaniesResponse update(Long id, CompaniesRequest request);

    void delete(Long id);
}
