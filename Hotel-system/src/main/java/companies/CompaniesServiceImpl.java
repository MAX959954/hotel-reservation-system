package companies;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompaniesServiceImpl implements CompaniesService {

    private final CompaniesRepository companiesRepository;

    @Override
    @Transactional
    public CompaniesResponse create(CompaniesRequest request) {
        if (companiesRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Company with that email already exists: " + request.getEmail());
        }
        return toResponse(companiesRepository.save(toEntity(request)));
    }

    @Override
    public CompaniesResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public CompaniesResponse getByEmail(String email) {
        return toResponse(companiesRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Company not found with that email: " + email)));
    }

    @Override
    public List<CompaniesResponse> getByCountry(String country) {
        return companiesRepository.findByCountry(country).stream().map(this::toResponse).toList();
    }

    @Override
    public List<CompaniesResponse> getByCity(String city) {
        return companiesRepository.findByCity(city).stream().map(this::toResponse).toList();
    }

    @Override
    public List<CompaniesResponse> getByStatus(CompaniesStatus status) {
        return companiesRepository.findByStatus(status).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public CompaniesResponse updateStatus(Long id, CompaniesStatus status) {
        Companies company = findById(id);
        company.setStatus(status);
        return toResponse(companiesRepository.save(company));
    }

    @Override
    @Transactional
    public CompaniesResponse update(Long id, CompaniesRequest request) {
        Companies company = findById(id);

        if (!company.getEmail().equals(request.getEmail()) &&
                companiesRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Company with that email already exists: " + request.getEmail());
        }

        company.setName(request.getName());
        company.setLegal_name(request.getLegalName());
        company.setEmail(request.getEmail());
        company.setPhone(request.getPhone());
        company.setAddress(request.getAddress());
        company.setCity(request.getCity());
        company.setCountry(request.getCountry());
        company.setWebsite(request.getWebSite());
        company.setLogo_url(request.getLogoUrl());
        return toResponse(companiesRepository.save(company));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        companiesRepository.delete(findById(id));
    }

    private Companies findById(Long id) {
        return companiesRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Company not found: " + id));
    }

    private Companies toEntity(CompaniesRequest request) {
        return Companies.builder()
                .name(request.getName())
                .legal_name(request.getLegalName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry())
                .website(request.getWebSite())
                .logo_url(request.getLogoUrl())
                .build();
    }

    private CompaniesResponse toResponse(Companies company) {
        return CompaniesResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .legalName(company.getLegal_name())
                .email(company.getEmail())
                .phone(company.getPhone())
                .address(company.getAddress())
                .city(company.getCity())
                .country(company.getCountry())
                .webSite(company.getWebsite())
                .logoUrl(company.getLogo_url())
                .status(company.getStatus())
                .created_at(company.getCreatedAt())
                .updated_at(company.getUpdatedAt())
                .build();
    }
}
