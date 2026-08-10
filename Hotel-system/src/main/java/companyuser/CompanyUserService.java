package companyuser;

import user.User;

import java.util.List;

public interface CompanyUserService {

    CompanyUserResponse invite(CompanyUserRequest request);

    CompanyUserResponse acceptInvite(Long id);

    CompanyUserResponse changeRole(Long id , CompanyRole role);

    void remove(Long id);

    List<CompanyUserResponse> getByCompany(Long companyId);

    /** Every company membership (any status) for whoever the JWT says is calling. */
    List<CompanyUserResponse> getMine();

    /** Called right after a new User is persisted — links any invites that were sent to
     *  this email before the account existed. Leaves status INVITED, so the existing
     *  accept-invite UI picks it up unchanged. */
    void linkPendingInvites(User newUser);
}
