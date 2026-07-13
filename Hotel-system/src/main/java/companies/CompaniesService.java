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

    CompaniesResponse update(Long id, CompaniesRequest request);

    void delete(Long id);
}
