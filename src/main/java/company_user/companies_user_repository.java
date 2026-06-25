package company_user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface companies_user_repository extends JpaRepository<Company_users, Long> {

    List<Company_users> findByCompanyId(Long companyId);

    List<Company_users> findByUserId (Long userId);

    List<Company_users> findByCompany_idIdAndCompany_role (Long companyId , Company_roles role) ;

    boolean existsByUser_idIdAndCompany_idId(Long userId, Long companyId);

}
