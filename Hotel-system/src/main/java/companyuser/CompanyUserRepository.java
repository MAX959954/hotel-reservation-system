package companyuser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CompanyUserRepository extends JpaRepository<CompanyUser, Long> {

    List<CompanyUser> findByCompanyId(Long companyId);

    List<CompanyUser> findByUserId(Long userId);

    @Query("SELECT cu FROM CompanyUser cu WHERE cu.company.id = :companyId AND cu.company_role = :role")
    List<CompanyUser> findByCompanyIdAndCompany_role(@Param("companyId") Long companyId, @Param("role") CompanyRole role);

    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);

    boolean existsByUserIdAndCompanyIdAndStatus(Long userId, Long companyId, CompanyUserStatus status);

    // The whole company-scoped permission rule in one indexed lookup (used on every
    // @PreAuthorize check): an ACTIVE member, of an ACTIVE company, holding one of the roles.
    @Query("SELECT CASE WHEN COUNT(cu) > 0 THEN true ELSE false END FROM CompanyUser cu " +
            "WHERE cu.company.id = :companyId AND cu.user.id = :userId " +
            "AND cu.status = companyuser.CompanyUserStatus.ACTIVE " +
            "AND cu.company.status = companies.CompaniesStatus.ACTIVE " +
            "AND cu.company_role IN :roles")
    boolean existsActiveMemberWithRole(@Param("companyId") Long companyId,
                                       @Param("userId") Long userId,
                                       @Param("roles") Collection<CompanyRole> roles);

    boolean existsByInvitedEmailAndCompanyId(String invitedEmail, Long companyId);

    /** Invites sent to an email before that person had an account — resolved once they
     *  register (see CompanyUserServiceImpl.linkPendingInvites). */
    List<CompanyUser> findByInvitedEmailAndUserIsNull(String invitedEmail);

}
