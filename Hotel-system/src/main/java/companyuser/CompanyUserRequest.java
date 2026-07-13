package companyuser;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompanyUserRequest {
    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "companyId is required")
    private Long companyId;

    @NotNull(message = "companyRole is required")
    private CompanyRole companyRole;
}
