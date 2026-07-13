package companyuser;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CompanyUserResponse {
    private Long id;
    private Long userId;
    private String userEmail;
    private Long companyId;
    private String companyName;
    private CompanyRole companyRole;
    private CompanyUserStatus status;
    private LocalDateTime invitedAt;
    private LocalDateTime joinedAt;
}
