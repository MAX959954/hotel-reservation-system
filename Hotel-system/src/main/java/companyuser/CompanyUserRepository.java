package companyuser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompanyUserRepository extends JpaRepository<CompanyUser, Long> {

    List<CompanyUser> findByCompanyId(Long companyId);

    List<CompanyUser> findByUserId(Long userId);

    @Query("SELECT cu FROM CompanyUser cu WHERE cu.company.id = :companyId AND cu.company_role = :role")
    List<CompanyUser> findByCompanyIdAndCompany_role(@Param("companyId") Long companyId, @Param("role") CompanyRole role);

    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);

    boolean existsByInvitedEmailAndCompanyId(String invitedEmail, Long companyId);

    /** Invites sent to an email before that person had an account — resolved once they
     *  register (see CompanyUserServiceImpl.linkPendingInvites). */
    List<CompanyUser> findByInvitedEmailAndUserIsNull(String invitedEmail);

}
