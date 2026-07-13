package companyuser;

import java.util.List;

public interface CompanyUserService {

    CompanyUserResponse invite(CompanyUserRequest request);

    CompanyUserResponse acceptInvite(Long id);

    CompanyUserResponse changeRole(Long id , CompanyRole role);

    void remove(Long id);

    List<CompanyUserResponse> getByCompany(Long companyId);
}
