package companies;

import companyuser.CompanyRole;
import companyuser.CompanyUser;
import companyuser.CompanyUserRepository;
import companyuser.CompanyUserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import user.MailService;
import user.Roles;
import user.User;
import user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompaniesServiceImpl implements CompaniesService {

    private final CompaniesRepository companiesRepository;
    private final UserRepository userRepository;
    private final CompanyUserRepository companyUserRepository;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CompaniesResponse create(CompaniesRequest request) {
        if (companiesRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Company with that email already exists: " + request.getEmail());
        }
        Companies company = toEntity(request);
        // Never trust a client-supplied submitter — this is what approve() later promotes
        // to HOTEL_MANAGER, so it must be exactly whoever is actually authenticated here.
        company.setSubmittedByUserId(currentUser().getId());
        return toResponse(companiesRepository.save(company));
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
    public CompaniesResponse approve(Long id) {
        Companies company = findById(id);
        if (company.getStatus() != CompaniesStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Only pending applications can be approved");
        }
        if (company.getSubmittedByUserId() == null) {
            throw new IllegalStateException("This company has no submitting user to promote");
        }

        User submitter = userRepository.findById(company.getSubmittedByUserId())
                .orElseThrow(() -> new IllegalStateException("Submitting user not found: " + company.getSubmittedByUserId()));

        company.setStatus(CompaniesStatus.ACTIVE);
        Companies saved = companiesRepository.save(company);

        if (!companyUserRepository.existsByUserIdAndCompanyId(submitter.getId(), saved.getId())) {
            CompanyUser owner = CompanyUser.builder()
                    .user(submitter)
                    .company(saved)
                    .company_role(CompanyRole.OWNER)
                    .status(CompanyUserStatus.ACTIVE)
                    .joined_at(LocalDateTime.now())
                    .build();
            companyUserRepository.save(owner);
        }

        if (submitter.getRoles().add(Roles.HOTEL_MANAGER)) {
            userRepository.save(submitter);
        }

        eventPublisher.publishEvent(new CompanyApplicationDecidedEvent(submitter.getEmail(), saved.getName(), true, null));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CompaniesResponse reject(Long id, String reason) {
        Companies company = findById(id);
        if (company.getStatus() != CompaniesStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Only pending applications can be rejected");
        }

        company.setStatus(CompaniesStatus.REJECTED);
        company.setRejectionReason(reason);
        Companies saved = companiesRepository.save(company);

        if (company.getSubmittedByUserId() != null) {
            userRepository.findById(company.getSubmittedByUserId())
                    .ifPresent(submitter -> eventPublisher.publishEvent(
                            new CompanyApplicationDecidedEvent(submitter.getEmail(), saved.getName(), false, reason)));
        }

        return toResponse(saved);
    }

    // Published now but delivered only after the transaction commits — same reasoning as
    // PaymentServiceImpl's confirmation email: the approve/reject decision must not depend
    // on mail delivery succeeding.
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendApplicationDecisionEmail(CompanyApplicationDecidedEvent event) {
        try {
            if (event.approved()) {
                mailService.sendCompanyApplicationApproved(event.email(), event.companyName());
            } else {
                mailService.sendCompanyApplicationRejected(event.email(), event.companyName(), event.reason());
            }
        } catch (Exception e) {
            log.warn("Could not deliver application decision email to {}: {}", event.email(), e.getMessage());
        }
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

    // Mirrors UserServiceImpl.currentUser(): the authenticated principal's name is the
    // email JwtAuthFilter put there, never a client-supplied id.
    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found by that email: " + email));
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
                .bankAccountHolder(request.getBankAccountHolder())
                .bankIban(request.getBankIban())
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
                .submittedByUserId(company.getSubmittedByUserId())
                .bankAccountHolder(company.getBankAccountHolder())
                .bankIban(company.getBankIban())
                .rejectionReason(company.getRejectionReason())
                .status(company.getStatus())
                .created_at(company.getCreatedAt())
                .updated_at(company.getUpdatedAt())
                .build();
    }
}
