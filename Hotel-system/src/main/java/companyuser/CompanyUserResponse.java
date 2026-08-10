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
    /** Set only while this invite is still pending against an email that hasn't
     *  registered yet — null once `userId`/`userEmail` are populated. */
    private String invitedEmail;
    private Long companyId;
    private String companyName;
    private CompanyRole companyRole;
    private CompanyUserStatus status;
    private LocalDateTime invitedAt;
    private LocalDateTime joinedAt;
}
