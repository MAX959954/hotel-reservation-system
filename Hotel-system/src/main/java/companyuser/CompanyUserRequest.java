package companyuser;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Exactly one of userId/email must be set — validated in CompanyUserServiceImpl.invite
 *  rather than here, since "at least one of two fields" isn't a single-field annotation. */
@Data
public class CompanyUserRequest {
    private Long userId;

    @Email
    private String email;

    @NotNull(message = "companyId is required")
    private Long companyId;

    @NotNull(message = "companyRole is required")
    private CompanyRole companyRole;
}
