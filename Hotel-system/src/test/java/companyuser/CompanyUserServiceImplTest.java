package companyuser;

import companies.Companies;
import companies.CompaniesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
public class CompanyUserServiceImplTest {

    @Mock
    private CompanyUserRepository companyUserRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CompaniesRepository companiesRepository;

    @InjectMocks
    private CompanyUserServiceImpl companyUserService;

    private User user;
    private Companies company;
    private CompanyUser member;
    private CompanyUserRequest request;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .build();

        company = Companies.builder()
                .id(1L)
                .name("Acme Hospitality")
                .build();

        member = CompanyUser.builder()
                .id(1L)
                .user(user)
                .company(company)
                .company_role(CompanyRole.STAFF)
                .status(CompanyUserStatus.INVITED)
                .build();

        request = new CompanyUserRequest();
        request.setUserId(1L);
        request.setCompanyId(1L);
        request.setCompanyRole(CompanyRole.STAFF);
    }

    // ---------- invite ----------

    @Test
    void invite_savesMemberAndReturnsResponse_whenNotAlreadyMember() {
        given(companyUserRepository.existsByUserIdAndCompanyId(1L, 1L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));
        given(companyUserRepository.save(any(CompanyUser.class))).willReturn(member);

        CompanyUserResponse response = companyUserService.invite(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUserEmail()).isEqualTo("jane@example.com");
        assertThat(response.getCompanyId()).isEqualTo(1L);
        assertThat(response.getCompanyName()).isEqualTo("Acme Hospitality");
        assertThat(response.getCompanyRole()).isEqualTo(CompanyRole.STAFF);
        assertThat(response.getStatus()).isEqualTo(CompanyUserStatus.INVITED);

        ArgumentCaptor<CompanyUser> captor = ArgumentCaptor.forClass(CompanyUser.class);
        verify(companyUserRepository).save(captor.capture());
        CompanyUser saved = captor.getValue();

        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getCompany()).isEqualTo(company);
        assertThat(saved.getCompany_role()).isEqualTo(CompanyRole.STAFF);
    }

    @Test
    void invite_throws_whenAlreadyMember() {
        given(companyUserRepository.existsByUserIdAndCompanyId(1L, 1L)).willReturn(true);

        assertThatThrownBy(() -> companyUserService.invite(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User is already is member of that company");

        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void invite_throws_whenUserNotFound() {
        given(companyUserRepository.existsByUserIdAndCompanyId(1L, 1L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyUserService.invite(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User not found by that id");

        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void invite_throws_whenCompanyNotFound() {
        given(companyUserRepository.existsByUserIdAndCompanyId(1L, 1L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(companiesRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyUserService.invite(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company not found by that id");

        verify(companyUserRepository, never()).save(any());
    }

    // ---------- acceptInvite ----------

    @Test
    void acceptInvite_activatesMember_whenInvited() {
        given(companyUserRepository.findById(1L)).willReturn(Optional.of(member));
        given(companyUserRepository.save(any(CompanyUser.class))).willReturn(member);

        CompanyUserResponse response = companyUserService.acceptInvite(1L);

        assertThat(response.getStatus()).isEqualTo(CompanyUserStatus.ACTIVE);

        ArgumentCaptor<CompanyUser> captor = ArgumentCaptor.forClass(CompanyUser.class);
        verify(companyUserRepository).save(captor.capture());
        CompanyUser saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(CompanyUserStatus.ACTIVE);
        assertThat(saved.getJoined_at()).isNotNull();
    }

    @Test
    void acceptInvite_throws_whenNotInvited() {
        member.setStatus(CompanyUserStatus.ACTIVE);
        given(companyUserRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> companyUserService.acceptInvite(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only invited members can accept invite");

        verify(companyUserRepository, never()).save(any());
    }

    @Test
    void acceptInvite_throws_whenMemberNotFound() {
        given(companyUserRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyUserService.acceptInvite(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company member not found by that id");

        verify(companyUserRepository, never()).save(any());
    }

    // ---------- changeRole ----------

    @Test
    void changeRole_updatesAndReturnsMember_whenFound() {
        given(companyUserRepository.findById(1L)).willReturn(Optional.of(member));
        given(companyUserRepository.save(any(CompanyUser.class))).willReturn(member);

        CompanyUserResponse response = companyUserService.changeRole(1L, CompanyRole.MANAGER);

        assertThat(response.getCompanyRole()).isEqualTo(CompanyRole.MANAGER);

        ArgumentCaptor<CompanyUser> captor = ArgumentCaptor.forClass(CompanyUser.class);
        verify(companyUserRepository).save(captor.capture());
        assertThat(captor.getValue().getCompany_role()).isEqualTo(CompanyRole.MANAGER);
    }

    @Test
    void changeRole_throws_whenMemberNotFound() {
        given(companyUserRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyUserService.changeRole(1L, CompanyRole.MANAGER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company member not found by that id");

        verify(companyUserRepository, never()).save(any());
    }

    // ---------- remove ----------

    @Test
    void remove_deletesMember_whenFound() {
        given(companyUserRepository.findById(1L)).willReturn(Optional.of(member));

        companyUserService.remove(1L);

        verify(companyUserRepository).delete(member);
    }

    @Test
    void remove_throws_whenMemberNotFound() {
        given(companyUserRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyUserService.remove(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company member not found by that id");
    }

    // ---------- getByCompany ----------

    @Test
    void getByCompany_returnsMembers() {
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(member));

        List<CompanyUserResponse> responses = companyUserService.getByCompany(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCompanyId()).isEqualTo(1L);
    }
}
