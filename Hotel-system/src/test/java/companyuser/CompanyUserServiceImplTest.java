package companyuser;

import companies.Companies;
import exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import companies.CompaniesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import user.MailService;
import user.User;
import user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompanyUserServiceImplTest {

    @Mock private CompanyUserRepository companyUserRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompaniesRepository companiesRepository;
    @Mock private MailService mailService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CompanyAuthorization companyAuth;

    @InjectMocks
    private CompanyUserServiceImpl companyUserService;

    private Companies company;

    @BeforeEach
    void setUp() {
        company = Companies.builder().id(1L).name("Ribeira Riverhouse Co").build();
    }

    // ---------- invite ----------

    @Test
    void invite_linksExistingUser_whenEmailResolvesToAnAccount() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));
        given(companyUserRepository.save(any(CompanyUser.class))).willAnswer(inv -> inv.getArgument(0));
        User existing = User.builder().id(9L).email("staff@example.com").build();
        given(userRepository.findByEmail("staff@example.com")).willReturn(Optional.of(existing));
        given(companyUserRepository.existsByUserIdAndCompanyId(9L, 1L)).willReturn(false);

        CompanyUserRequest request = new CompanyUserRequest();
        request.setEmail("staff@example.com");
        request.setCompanyId(1L);
        request.setCompanyRole(CompanyRole.RECEPTIONIST);

        CompanyUserResponse response = companyUserService.invite(request);

        assertThat(response.getUserId()).isEqualTo(9L);
        assertThat(response.getInvitedEmail()).isNull();
        verify(eventPublisher).publishEvent(any(CompanyInviteEvent.class));
    }

    @Test
    void invite_createsPendingRow_whenEmailHasNoAccountYet() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));
        given(companyUserRepository.save(any(CompanyUser.class))).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findByEmail("newstaff@example.com")).willReturn(Optional.empty());

        CompanyUserRequest request = new CompanyUserRequest();
        request.setEmail("newstaff@example.com");
        request.setCompanyId(1L);
        request.setCompanyRole(CompanyRole.RECEPTIONIST);

        CompanyUserResponse response = companyUserService.invite(request);

        assertThat(response.getUserId()).isNull();
        assertThat(response.getInvitedEmail()).isEqualTo("newstaff@example.com");
        assertThat(response.getStatus()).isEqualTo(CompanyUserStatus.INVITED);
        verify(eventPublisher).publishEvent(any(CompanyInviteEvent.class));
    }

    @Test
    void invite_throws_whenPendingInviteAlreadyExistsForThatEmail() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));
        given(userRepository.findByEmail("dup@example.com")).willReturn(Optional.empty());
        given(companyUserRepository.existsByInvitedEmailAndCompanyId("dup@example.com", 1L)).willReturn(true);

        CompanyUserRequest request = new CompanyUserRequest();
        request.setEmail("dup@example.com");
        request.setCompanyId(1L);
        request.setCompanyRole(CompanyRole.STAFF);

        assertThatThrownBy(() -> companyUserService.invite(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a pending invite");

        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void invite_throws_whenNeitherUserIdNorEmailGiven() {
        CompanyUserRequest request = new CompanyUserRequest();
        request.setCompanyId(1L);
        request.setCompanyRole(CompanyRole.STAFF);

        assertThatThrownBy(() -> companyUserService.invite(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId or email");
    }

    // ---------- linkPendingInvites ----------

    @Test
    void linkPendingInvites_attachesNewUser_toMatchingPendingRows() {
        CompanyUser pending = CompanyUser.builder()
                .id(20L)
                .invitedEmail("newstaff@example.com")
                .company(company)
                .company_role(CompanyRole.RECEPTIONIST)
                .status(CompanyUserStatus.INVITED)
                .build();
        given(companyUserRepository.findByInvitedEmailAndUserIsNull("newstaff@example.com"))
                .willReturn(List.of(pending));
        given(companyUserRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));

        User newUser = User.builder().id(42L).email("newstaff@example.com").build();
        companyUserService.linkPendingInvites(newUser);

        assertThat(pending.getUser()).isEqualTo(newUser);
        assertThat(pending.getInvitedEmail()).isNull();
        // Still INVITED, not auto-accepted — the guest confirms via the existing accept UI.
        assertThat(pending.getStatus()).isEqualTo(CompanyUserStatus.INVITED);
    }

    @Test
    void linkPendingInvites_isNoOp_whenNothingPendingForThatEmail() {
        given(companyUserRepository.findByInvitedEmailAndUserIsNull("nobody@example.com"))
                .willReturn(List.of());

        User newUser = User.builder().id(1L).email("nobody@example.com").build();
        companyUserService.linkPendingInvites(newUser);

        verify(companyUserRepository).saveAll(List.of());
    }

    // ---------- OWNER role protection ----------

    private CompanyUser member(Long id, CompanyRole role, CompanyUserStatus status) {
        return CompanyUser.builder()
                .id(id)
                .user(User.builder().id(id).email("m" + id + "@example.com").build())
                .company(company)
                .company_role(role)
                .status(status)
                .build();
    }

    @Test
    void invite_throws_whenNonOwnerInvitesAnOwner() {
        given(companyAuth.canManageOwners(1L)).willReturn(false);

        CompanyUserRequest request = new CompanyUserRequest();
        request.setEmail("new@example.com");
        request.setCompanyId(1L);
        request.setCompanyRole(CompanyRole.OWNER);

        assertThatThrownBy(() -> companyUserService.invite(request))
                .isInstanceOf(AccessDeniedException.class);
        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void changeRole_throws_whenManagerPromotesSomeoneToOwner() {
        CompanyUser manager = member(5L, CompanyRole.MANAGER, CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(5L)).willReturn(Optional.of(manager));
        given(companyAuth.canManageOwners(1L)).willReturn(false);

        assertThatThrownBy(() -> companyUserService.changeRole(5L, CompanyRole.OWNER))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(manager.getCompany_role()).isEqualTo(CompanyRole.MANAGER);
        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void changeRole_throws_whenManagerDemotesTheOwner() {
        CompanyUser owner = member(1L, CompanyRole.OWNER, CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(1L)).willReturn(Optional.of(owner));
        given(companyAuth.canManageOwners(1L)).willReturn(false);

        assertThatThrownBy(() -> companyUserService.changeRole(1L, CompanyRole.STAFF))
                .isInstanceOf(AccessDeniedException.class);
        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void changeRole_allowsOwnerToPromoteManager() {
        CompanyUser manager = member(5L, CompanyRole.MANAGER, CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(5L)).willReturn(Optional.of(manager));
        given(companyAuth.canManageOwners(1L)).willReturn(true);
        given(companyUserRepository.save(any(CompanyUser.class))).willAnswer(inv -> inv.getArgument(0));

        CompanyUserResponse response = companyUserService.changeRole(5L, CompanyRole.OWNER);

        assertThat(response.getCompanyRole()).isEqualTo(CompanyRole.OWNER);
    }

    @Test
    void changeRole_allowsManagerToChangeNonOwnerRoles_withoutOwnerCheck() {
        CompanyUser staff = member(6L, CompanyRole.STAFF, CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(6L)).willReturn(Optional.of(staff));
        given(companyUserRepository.save(any(CompanyUser.class))).willAnswer(inv -> inv.getArgument(0));

        CompanyUserResponse response = companyUserService.changeRole(6L, CompanyRole.RECEPTIONIST);

        assertThat(response.getCompanyRole()).isEqualTo(CompanyRole.RECEPTIONIST);
        verify(companyAuth, never()).canManageOwners(any());
    }

    @Test
    void changeRole_throws_whenDemotingTheLastActiveOwner() {
        CompanyUser owner = member(1L, CompanyRole.OWNER, CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(1L)).willReturn(Optional.of(owner));
        given(companyAuth.canManageOwners(1L)).willReturn(true);
        given(companyUserRepository.findByCompanyIdAndCompany_role(1L, CompanyRole.OWNER))
                .willReturn(List.of(owner));

        assertThatThrownBy(() -> companyUserService.changeRole(1L, CompanyRole.MANAGER))
                .isInstanceOf(ConflictException.class);
        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void changeRole_allowsDemotingAnOwner_whenAnotherActiveOwnerRemains() {
        CompanyUser owner = member(1L, CompanyRole.OWNER, CompanyUserStatus.ACTIVE);
        CompanyUser coOwner = member(2L, CompanyRole.OWNER, CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(1L)).willReturn(Optional.of(owner));
        given(companyAuth.canManageOwners(1L)).willReturn(true);
        given(companyUserRepository.findByCompanyIdAndCompany_role(1L, CompanyRole.OWNER))
                .willReturn(List.of(owner, coOwner));
        given(companyUserRepository.save(any(CompanyUser.class))).willAnswer(inv -> inv.getArgument(0));

        CompanyUserResponse response = companyUserService.changeRole(1L, CompanyRole.MANAGER);

        assertThat(response.getCompanyRole()).isEqualTo(CompanyRole.MANAGER);
    }

    @Test
    void remove_throws_whenManagerRemovesTheOwner() {
        CompanyUser owner = member(1L, CompanyRole.OWNER, CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(1L)).willReturn(Optional.of(owner));
        given(companyAuth.canManageOwners(1L)).willReturn(false);

        assertThatThrownBy(() -> companyUserService.remove(1L))
                .isInstanceOf(AccessDeniedException.class);
        verify(companyUserRepository, never()).delete(any());
    }

    @Test
    void remove_throws_whenRemovingTheLastActiveOwner() {
        CompanyUser owner = member(1L, CompanyRole.OWNER, CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(1L)).willReturn(Optional.of(owner));
        given(companyAuth.canManageOwners(1L)).willReturn(true);
        given(companyUserRepository.findByCompanyIdAndCompany_role(1L, CompanyRole.OWNER))
                .willReturn(List.of(owner));

        assertThatThrownBy(() -> companyUserService.remove(1L))
                .isInstanceOf(ConflictException.class);
        verify(companyUserRepository, never()).delete(any());
    }

    @Test
    void remove_deletesNonOwnerMember() {
        CompanyUser staff = member(6L, CompanyRole.STAFF, CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(6L)).willReturn(Optional.of(staff));

        companyUserService.remove(6L);

        verify(companyUserRepository).delete(staff);
    }
}
