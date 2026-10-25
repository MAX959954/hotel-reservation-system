package com.folio.companies;

// Carries everything the approval/rejection email needs out of the transaction that made
// the decision — the listener sending it runs after commit (see CompanyServiceImpl),
// by which point the Company/User entities may no longer be attached to an open session.
record CompanyApplicationDecidedEvent(
        String email,
        String companyName,
        boolean approved,
        String reason
) {
}
