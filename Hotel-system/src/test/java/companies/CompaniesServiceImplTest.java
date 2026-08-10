package companies;

import companyuser.CompanyRole;
import companyuser.CompanyUser;
import companyuser.CompanyUserRepository;
import companyuser.CompanyUserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import user.MailService;
import user.Roles;
import user.User;
import user.UserRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompaniesServiceImplTest {

    @Mock private CompaniesRepository companiesRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompanyUserRepository companyUserRepository;
    @Mock private MailService mailService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CompaniesServiceImpl companiesService;

    private Companies pendingCompany;
    private User submitter;

    @BeforeEach
    void setUp() {
        submitter = User.builder().id(5L).email("owner@example.com").roles(new HashSet<>(Set.of(Roles.GUEST))).build();
        pendingCompany = Companies.builder()
                .id(1L)
                .name("Ribeira Riverhouse Co")
                .status(CompaniesStatus.PENDING_VERIFICATION)
                .submittedByUserId(5L)
                .build();
    }

    // ---------- create ----------

    @Test
    void create_setsSubmittedByUserId_fromAuthenticatedCaller() {
        CompaniesRequest request = new CompaniesRequest();
        request.setName("New Co");
        request.setLegalName("New Co Ltd");
        request.setEmail("newco@example.com");
        request.setPhone("+1");
        request.setAddress("Addr");
        request.setCity("City");
        request.setCountry("Country");
        request.setWebSite("https://newco.example");

        given(companiesRepository.existsByEmail("newco@example.com")).willReturn(false);
        given(companiesRepository.save(any(Companies.class))).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findByEmail("owner@example.com")).willReturn(Optional.of(submitter));

        try (MockedStatic<SecurityContextHolder> mocked = Mockito.mockStatic(SecurityContextHolder.class)) {
            SecurityContext context = org.mockito.Mockito.mock(SecurityContext.class);
            Authentication auth = org.mockito.Mockito.mock(Authentication.class);
            given(auth.getName()).willReturn("owner@example.com");
            given(context.getAuthentication()).willReturn(auth);
            mocked.when(SecurityContextHolder::getContext).thenReturn(context);

            CompaniesResponse response = companiesService.create(request);

            assertThat(response.getSubmittedByUserId()).isEqualTo(5L);
        }

        ArgumentCaptor<Companies> captor = ArgumentCaptor.forClass(Companies.class);
        verify(companiesRepository).save(captor.capture());
        assertThat(captor.getValue().getSubmittedByUserId()).isEqualTo(5L);
    }

    // ---------- approve ----------

    @Test
    void approve_movesToActive_grantsHotelManager_createsOwnerMembership() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));
        given(companiesRepository.save(any(Companies.class))).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(5L)).willReturn(Optional.of(submitter));
        given(companyUserRepository.existsByUserIdAndCompanyId(5L, 1L)).willReturn(false);

        CompaniesResponse response = companiesService.approve(1L);

        assertThat(response.getStatus()).isEqualTo(CompaniesStatus.ACTIVE);
        assertThat(submitter.getRoles()).contains(Roles.HOTEL_MANAGER);
        verify(userRepository).save(submitter);

        ArgumentCaptor<CompanyUser> memberCaptor = ArgumentCaptor.forClass(CompanyUser.class);
        verify(companyUserRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getCompany_role()).isEqualTo(CompanyRole.OWNER);
        assertThat(memberCaptor.getValue().getStatus()).isEqualTo(CompanyUserStatus.ACTIVE);

        verify(eventPublisher).publishEvent(any(CompanyApplicationDecidedEvent.class));
    }

    @Test
    void approve_doesNotDuplicateMembership_whenOwnerRowAlreadyExists() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));
        given(companiesRepository.save(any(Companies.class))).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(5L)).willReturn(Optional.of(submitter));
        given(companyUserRepository.existsByUserIdAndCompanyId(5L, 1L)).willReturn(true);

        companiesService.approve(1L);

        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void approve_throws_whenNotPending() {
        pendingCompany.setStatus(CompaniesStatus.ACTIVE);
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));

        assertThatThrownBy(() -> companiesService.approve(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending applications can be approved");

        verify(userRepository, never()).save(any());
    }

    @Test
    void approve_throws_whenNoSubmitter() {
        pendingCompany.setSubmittedByUserId(null);
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));

        assertThatThrownBy(() -> companiesService.approve(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no submitting user");
    }

    // ---------- reject ----------

    @Test
    void reject_movesToRejected_storesReason() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));
        given(companiesRepository.save(any(Companies.class))).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(5L)).willReturn(Optional.of(submitter));

        CompaniesResponse response = companiesService.reject(1L, "Missing documents");

        assertThat(response.getStatus()).isEqualTo(CompaniesStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Missing documents");
        verify(eventPublisher).publishEvent(any(CompanyApplicationDecidedEvent.class));
        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void reject_throws_whenNotPending() {
        pendingCompany.setStatus(CompaniesStatus.REJECTED);
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));

        assertThatThrownBy(() -> companiesService.reject(1L, "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending applications can be rejected");
    }
}
