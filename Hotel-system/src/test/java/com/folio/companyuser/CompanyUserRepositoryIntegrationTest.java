package com.folio.companyuser;

import com.folio.companies.Company;
import com.folio.companies.CompanyRepository;
import com.folio.companies.CompanyStatus;
import com.folio.HotelSystemApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.folio.user.Role;
import com.folio.user.User;
import com.folio.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// The company-scoped permission rule used by every @companyAuth.hasRole check lives in
// one JPQL query (CompanyUserRepository.existsActiveMemberWithRole), so it is checked
// here against a real PostgreSQL database rather than around a mock.
@SpringBootTest(classes = HotelSystemApplication.class)
@ActiveProfiles("test")
@Transactional
class CompanyUserRepositoryIntegrationTest {

    @Autowired
    private CompanyUserRepository companyUserRepository;

    @Autowired
    private CompanyRepository companiesRepository;

    @Autowired
    private UserRepository userRepository;

    private Company company;
    private User member;

    @BeforeEach
    void setUp() {
        company = companiesRepository.save(Company.builder()
                .name("Acme Hospitality")
                .legalName("Acme Hospitality LLC")
                .email("contact@acme.com")
                .phone("+123456789")
                .address("1 Main St")
                .city("Paris")
                .country("France")
                .website("https://acme.com")
                .status(CompanyStatus.ACTIVE)
                .build());

        member = userRepository.save(User.builder()
                .firstName("Mia")
                .lastName("Manager")
                .email("mia@acme.com")
                .passwordHash("hashed")
                .phone("+100000002")
                .roles(Set.of(Role.HOTEL_MANAGER))
                .emailVerified(true)
                .enabled(true)
                .build());
    }

    private void addMembership(CompanyRole role, CompanyUserStatus status) {
        companyUserRepository.save(CompanyUser.builder()
                .user(member)
                .company(company)
                .companyRole(role)
                .status(status)
                .joinedAt(LocalDateTime.now())
                .build());
    }

    private boolean hasRole(CompanyRole... roles) {
        return companyUserRepository.existsActiveMemberWithRole(company.getId(), member.getId(), List.of(roles));
    }

    @Test
    void true_forActiveMemberOfActiveCompany_withOneOfTheRoles() {
        addMembership(CompanyRole.MANAGER, CompanyUserStatus.ACTIVE);

        assertThat(hasRole(CompanyRole.OWNER, CompanyRole.MANAGER)).isTrue();
    }

    @Test
    void false_whenRoleDoesNotMatch() {
        addMembership(CompanyRole.STAFF, CompanyUserStatus.ACTIVE);

        assertThat(hasRole(CompanyRole.OWNER, CompanyRole.MANAGER)).isFalse();
    }

    @Test
    void false_whenMembershipIsNotActive() {
        addMembership(CompanyRole.MANAGER, CompanyUserStatus.INVITED);

        assertThat(hasRole(CompanyRole.MANAGER)).isFalse();
    }

    @Test
    void false_whenCompanyIsSuspended() {
        addMembership(CompanyRole.OWNER, CompanyUserStatus.ACTIVE);
        company.setStatus(CompanyStatus.SUSPENDED);
        companiesRepository.saveAndFlush(company);

        assertThat(hasRole(CompanyRole.OWNER)).isFalse();
    }

    @Test
    void false_whenUserIsNotAMember() {
        assertThat(hasRole(CompanyRole.OWNER)).isFalse();
    }

    @Test
    void existsByStatus_onlyCountsActiveMembership() {
        addMembership(CompanyRole.STAFF, CompanyUserStatus.SUSPENDED);

        assertThat(companyUserRepository.existsByUserIdAndCompanyIdAndStatus(
                member.getId(), company.getId(), CompanyUserStatus.ACTIVE)).isFalse();
    }
}
