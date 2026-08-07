package user;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class UserProfileResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String avatarUrl;
    /** Read-only in the client — it was age-verified at registration; changing it here would bypass that. */
    private LocalDate dateOfBirth;
    private boolean emailVerified;
    private AccountStatus accountStatus;
    private Set<Roles> roles;
    private LocalDateTime createdAt;
}
