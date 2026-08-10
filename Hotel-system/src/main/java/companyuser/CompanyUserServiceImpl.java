package companyuser;

import companies.Companies;
import companies.CompaniesRepository;
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
import user.User;
import user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyUserServiceImpl implements CompanyUserService {

    private final CompanyUserRepository companyUserRepository;
    private final UserRepository userRepository;
    private final CompaniesRepository companiesRepository;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CompanyUserResponse invite (CompanyUserRequest request) {
        if (request.getUserId() == null && (request.getEmail() == null || request.getEmail().isBlank())) {
            throw new IllegalStateException("Either userId or email is required");
        }

        Companies company = companiesRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new IllegalStateException("Company not found by that id " + request.getCompanyId()));

        // A userId (or an email that resolves to an existing account) behaves exactly as
        // before. An email with no matching account yet gets a row with no user attached —
        // linkPendingInvites() fills that in the moment that address registers.
        User existingUser = request.getUserId() != null
                ? userRepository.findById(request.getUserId())
                        .orElseThrow(() -> new IllegalStateException("User not found by that id " + request.getUserId()))
                : userRepository.findByEmail(request.getEmail()).orElse(null);

        CompanyUser member;
        if (existingUser != null) {
            if (companyUserRepository.existsByUserIdAndCompanyId(existingUser.getId(), request.getCompanyId())) {
                throw new IllegalStateException("User is already is member of that company");
            }
            member = CompanyUser.builder()
                    .user(existingUser)
                    .company(company)
                    .company_role(request.getCompanyRole())
                    .build();
        } else {
            String email = request.getEmail().trim().toLowerCase();
            if (companyUserRepository.existsByInvitedEmailAndCompanyId(email, request.getCompanyId())) {
                throw new IllegalStateException("That email already has a pending invite to that company");
            }
            member = CompanyUser.builder()
                    .invitedEmail(email)
                    .company(company)
                    .company_role(request.getCompanyRole())
                    .build();
        }

        CompanyUser saved = companyUserRepository.save(member);
        String inviteeEmail = existingUser != null ? existingUser.getEmail() : member.getInvitedEmail();
        eventPublisher.publishEvent(new CompanyInviteEvent(inviteeEmail, company.getName(), request.getCompanyRole().name()));

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void linkPendingInvites(User newUser) {
        List<CompanyUser> pending = companyUserRepository.findByInvitedEmailAndUserIsNull(newUser.getEmail());
        for (CompanyUser member : pending) {
            member.setUser(newUser);
            member.setInvitedEmail(null);
        }
        companyUserRepository.saveAll(pending);
    }

    // Published now but delivered only after the transaction commits — same pattern as
    // PaymentServiceImpl's confirmation email.
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendInviteEmail(CompanyInviteEvent event) {
        try {
            mailService.sendCompanyInvite(event.email(), event.companyName(), event.role());
        } catch (Exception e) {
            log.warn("Could not deliver company invite email to {}: {}", event.email(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public CompanyUserResponse acceptInvite(Long id) {
        CompanyUser member = findById(id);

        if(member.getStatus() != CompanyUserStatus.INVITED) {
            throw new IllegalStateException("Only invited members can accept invite , current status: " + member.getStatus());
        }

        member.setStatus(CompanyUserStatus.ACTIVE);
        member.setJoined_at(LocalDateTime.now());
        return toResponse(companyUserRepository.save(member));
    }

    @Override
    @Transactional
    public CompanyUserResponse changeRole(Long id , CompanyRole role) {
        CompanyUser member = findById(id);
        member.setCompany_role(role);
        return toResponse(companyUserRepository.save(member));
    }

    @Override
    @Transactional
    public void  remove (Long id) {
        companyUserRepository.delete(findById(id));
    }

    @Override
    public List<CompanyUserResponse> getByCompany(Long companyId) {
        return companyUserRepository.findByCompanyId(companyId).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<CompanyUserResponse> getMine() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found by that email: " + email));
        return companyUserRepository.findByUserId(user.getId()).stream().map(this :: toResponse).toList();
    }

    private CompanyUser findById(Long id) {
        return companyUserRepository.findById(id).orElseThrow(() -> new IllegalStateException("Company member not found by that id " + id));
    }

    private CompanyUserResponse toResponse(CompanyUser member){
        return CompanyUserResponse.builder()
                .id(member.getId())
                .userId(member.getUser() != null ? member.getUser().getId() : null)
                .userEmail(member.getUser() != null ? member.getUser().getEmail() : null)
                .invitedEmail(member.getInvitedEmail())
                .companyId(member.getCompany().getId())
                .companyName(member.getCompany().getName())
                .companyRole(member.getCompany_role())
                .status(member.getStatus())
                .invitedAt(member.getInvited_at())
                .joinedAt(member.getJoined_at())
                .build();
    }


}
