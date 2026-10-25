package com.folio.companies;

import com.folio.booking.BookingService;
import com.folio.companyuser.CompanyRole;
import com.folio.companyuser.CompanyUser;
import com.folio.companyuser.CompanyUserRepository;
import com.folio.companyuser.CompanyUserStatus;
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
import com.folio.user.MailService;
import com.folio.user.Role;
import com.folio.user.User;
import com.folio.user.UserRepository;

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
class CompanyServiceImplTest {

    @Mock private CompanyRepository companiesRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompanyUserRepository companyUserRepository;
    @Mock private MailService mailService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private BookingService bookingService;

    @InjectMocks
    private CompanyServiceImpl companiesService;

    private Company pendingCompany;
    private User submitter;

    @BeforeEach
    void setUp() {
        submitter = User.builder().id(5L).email("owner@example.com").roles(new HashSet<>(Set.of(Role.GUEST))).build();
        pendingCompany = Company.builder()
                .id(1L)
                .name("Ribeira Riverhouse Co")
                .status(CompanyStatus.PENDING_VERIFICATION)
                .submittedByUserId(5L)
                .build();
    }

    // ---------- create ----------

    @Test
    void create_setsSubmittedByUserId_fromAuthenticatedCaller() {
        CompanyRequest request = new CompanyRequest();
        request.setName("New Co");
        request.setLegalName("New Co Ltd");
        request.setEmail("newco@example.com");
        request.setPhone("+1");
        request.setAddress("Addr");
        request.setCity("City");
        request.setCountry("Country");
        request.setWebSite("https://newco.example");

        given(companiesRepository.existsByEmail("newco@example.com")).willReturn(false);
        given(companiesRepository.save(any(Company.class))).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findByEmail("owner@example.com")).willReturn(Optional.of(submitter));

        try (MockedStatic<SecurityContextHolder> mocked = Mockito.mockStatic(SecurityContextHolder.class)) {
            SecurityContext context = org.mockito.Mockito.mock(SecurityContext.class);
            Authentication auth = org.mockito.Mockito.mock(Authentication.class);
            given(auth.getName()).willReturn("owner@example.com");
            given(context.getAuthentication()).willReturn(auth);
            mocked.when(SecurityContextHolder::getContext).thenReturn(context);

            CompanyResponse response = companiesService.create(request);

            assertThat(response.getSubmittedByUserId()).isEqualTo(5L);
        }

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companiesRepository).save(captor.capture());
        assertThat(captor.getValue().getSubmittedByUserId()).isEqualTo(5L);
    }

    // ---------- approve ----------

    @Test
    void approve_movesToActive_grantsHotelManager_createsOwnerMembership() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));
        given(companiesRepository.save(any(Company.class))).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(5L)).willReturn(Optional.of(submitter));
        given(companyUserRepository.existsByUserIdAndCompanyId(5L, 1L)).willReturn(false);

        CompanyResponse response = companiesService.approve(1L);

        assertThat(response.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(submitter.getRoles()).contains(Role.HOTEL_MANAGER);
        verify(userRepository).save(submitter);

        ArgumentCaptor<CompanyUser> memberCaptor = ArgumentCaptor.forClass(CompanyUser.class);
        verify(companyUserRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getCompanyRole()).isEqualTo(CompanyRole.OWNER);
        assertThat(memberCaptor.getValue().getStatus()).isEqualTo(CompanyUserStatus.ACTIVE);

        verify(eventPublisher).publishEvent(any(CompanyApplicationDecidedEvent.class));
    }

    @Test
    void approve_doesNotDuplicateMembership_whenOwnerRowAlreadyExists() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));
        given(companiesRepository.save(any(Company.class))).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(5L)).willReturn(Optional.of(submitter));
        given(companyUserRepository.existsByUserIdAndCompanyId(5L, 1L)).willReturn(true);

        companiesService.approve(1L);

        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void approve_throws_whenNotPending() {
        pendingCompany.setStatus(CompanyStatus.ACTIVE);
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
        given(companiesRepository.save(any(Company.class))).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(5L)).willReturn(Optional.of(submitter));

        CompanyResponse response = companiesService.reject(1L, "Missing documents");

        assertThat(response.getStatus()).isEqualTo(CompanyStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Missing documents");
        verify(eventPublisher).publishEvent(any(CompanyApplicationDecidedEvent.class));
        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void reject_throws_whenNotPending() {
        pendingCompany.setStatus(CompanyStatus.REJECTED);
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));

        assertThatThrownBy(() -> companiesService.reject(1L, "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending applications can be rejected");
    }

    // ---------- updateStatus ----------

    @Test
    void updateStatus_cancelsUpcomingBookings_whenCompanyIsSuspended() {
        pendingCompany.setStatus(CompanyStatus.ACTIVE);
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));
        given(companiesRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

        CompanyResponse response = companiesService.updateStatus(1L, CompanyStatus.SUSPENDED);

        assertThat(response.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
        // Its staff lose access now — nobody else would ever refund these guests.
        verify(bookingService).cancelUpcomingForCompany(1L);
    }

    @Test
    void updateStatus_keepsBookings_whenCompanyIsReactivated() {
        pendingCompany.setStatus(CompanyStatus.SUSPENDED);
        given(companiesRepository.findById(1L)).willReturn(Optional.of(pendingCompany));
        given(companiesRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

        companiesService.updateStatus(1L, CompanyStatus.ACTIVE);

        verify(bookingService, never()).cancelUpcomingForCompany(any());
    }
}
