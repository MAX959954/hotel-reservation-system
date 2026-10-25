package com.folio.companies;

import com.folio.booking.BookingService;
import com.folio.exception.ResourceNotFoundException;
import com.folio.companyuser.CompanyRole;
import com.folio.companyuser.CompanyUser;
import com.folio.companyuser.CompanyUserRepository;
import com.folio.companyuser.CompanyUserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.folio.user.MailService;
import com.folio.user.Role;
import com.folio.user.User;
import com.folio.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companiesRepository;
    private final UserRepository userRepository;
    private final CompanyUserRepository companyUserRepository;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingService bookingService;

    @Override
    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        if (companiesRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Company with that email already exists: " + request.getEmail());
        }
        Company company = toEntity(request);
        // Never trust a client-supplied submitter — this is what approve() later promotes
        // to HOTEL_MANAGER, so it must be exactly whoever is actually authenticated here.
        company.setSubmittedByUserId(currentUser().getId());
        return toResponse(companiesRepository.save(company));
    }

    @Override
    public CompanyResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public CompanyResponse getByEmail(String email) {
        return toResponse(companiesRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with that email: " + email)));
    }

    @Override
    public List<CompanyResponse> getByCountry(String country) {
        return companiesRepository.findByCountry(country).stream().map(this::toResponse).toList();
    }

    @Override
    public List<CompanyResponse> getByCity(String city) {
        return companiesRepository.findByCity(city).stream().map(this::toResponse).toList();
    }

    @Override
    public List<CompanyResponse> getByStatus(CompanyStatus status) {
        return companiesRepository.findByStatus(status).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    // Public hotel search and availability filter on the company's status, so cached
    // results must not keep showing a just-suspended company's hotels.
    @CacheEvict(cacheNames = {"hotelById", "hotelsByCity", "hotelsByCountry", "hotelsByCompany",
            "hotelsByRating", "hotelsByType", "roomsAvailability"}, allEntries = true)
    public CompanyResponse updateStatus(Long id, CompanyStatus status) {
        Company company = findById(id);
        company.setStatus(status);
        Company saved = companiesRepository.save(company);
        // Outside ACTIVE the company's staff lose every permission (CompanyAuthorization),
        // so its upcoming bookings would be left with nobody to check guests in, cancel or
        // refund them — and the scheduler would eventually mark them NO_SHOW, keeping the
        // guests' money. They are cancelled with a full refund instead, in this same
        // transaction: if a refund fails, the status change is rejected too.
        if (status != CompanyStatus.ACTIVE) {
            int cancelled = bookingService.cancelUpcomingForCompany(id);
            if (cancelled > 0) {
                log.info("Company {} set to {}: cancelled {} upcoming booking(s)", id, status, cancelled);
            }
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CompanyResponse approve(Long id) {
        Company company = findById(id);
        if (company.getStatus() != CompanyStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Only pending applications can be approved");
        }
        if (company.getSubmittedByUserId() == null) {
            throw new IllegalStateException("This company has no submitting user to promote");
        }

        User submitter = userRepository.findById(company.getSubmittedByUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Submitting user not found: " + company.getSubmittedByUserId()));

        company.setStatus(CompanyStatus.ACTIVE);
        Company saved = companiesRepository.save(company);

        if (!companyUserRepository.existsByUserIdAndCompanyId(submitter.getId(), saved.getId())) {
            CompanyUser owner = CompanyUser.builder()
                    .user(submitter)
                    .company(saved)
                    .companyRole(CompanyRole.OWNER)
                    .status(CompanyUserStatus.ACTIVE)
                    .joinedAt(LocalDateTime.now())
                    .build();
            companyUserRepository.save(owner);
        }

        if (submitter.getRoles().add(Role.HOTEL_MANAGER)) {
            userRepository.save(submitter);
        }

        eventPublisher.publishEvent(new CompanyApplicationDecidedEvent(submitter.getEmail(), saved.getName(), true, null));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CompanyResponse reject(Long id, String reason) {
        Company company = findById(id);
        if (company.getStatus() != CompanyStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Only pending applications can be rejected");
        }

        company.setStatus(CompanyStatus.REJECTED);
        company.setRejectionReason(reason);
        Company saved = companiesRepository.save(company);

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
    public CompanyResponse update(Long id, CompanyRequest request) {
        Company company = findById(id);

        if (!company.getEmail().equals(request.getEmail()) &&
                companiesRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Company with that email already exists: " + request.getEmail());
        }

        company.setName(request.getName());
        company.setLegalName(request.getLegalName());
        company.setEmail(request.getEmail());
        company.setPhone(request.getPhone());
        company.setAddress(request.getAddress());
        company.setCity(request.getCity());
        company.setCountry(request.getCountry());
        company.setWebsite(request.getWebSite());
        company.setLogoUrl(request.getLogoUrl());
        return toResponse(companiesRepository.save(company));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        companiesRepository.delete(findById(id));
    }

    private Company findById(Long id) {
        return companiesRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id));
    }

    // Mirrors UserServiceImpl.currentUser(): the authenticated principal's name is the
    // email JwtAuthFilter put there, never a client-supplied id.
    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found by that email: " + email));
    }

    private Company toEntity(CompanyRequest request) {
        return Company.builder()
                .name(request.getName())
                .legalName(request.getLegalName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry())
                .website(request.getWebSite())
                .logoUrl(request.getLogoUrl())
                .bankAccountHolder(request.getBankAccountHolder())
                .bankIban(request.getBankIban())
                .build();
    }

    private CompanyResponse toResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .legalName(company.getLegalName())
                .email(company.getEmail())
                .phone(company.getPhone())
                .address(company.getAddress())
                .city(company.getCity())
                .country(company.getCountry())
                .webSite(company.getWebsite())
                .logoUrl(company.getLogoUrl())
                .submittedByUserId(company.getSubmittedByUserId())
                .bankAccountHolder(company.getBankAccountHolder())
                .bankIban(company.getBankIban())
                .rejectionReason(company.getRejectionReason())
                .status(company.getStatus())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }
}
