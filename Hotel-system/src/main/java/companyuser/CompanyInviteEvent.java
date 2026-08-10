package companyuser;

// Fires for both branches of CompanyUserServiceImpl.invite — an existing user gets emailed
// immediately, and an unregistered email gets emailed too since it's the only way they'd
// find out (see CompanyUserServiceImpl.linkPendingInvites for the registration-time link-up).
record CompanyInviteEvent(
        String email,
        String companyName,
        String role
) {
}
