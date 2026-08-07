package user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Deliberately narrow: name and phone only. Email and password change are a different
 * security surface (re-verification, session invalidation) this endpoint doesn't take on,
 * and date of birth is intentionally absent — it was age-verified at registration, so
 * letting it be edited here would be a way to quietly bypass that check.
 */
@Data
public class UpdateProfileRequest {

    @NotBlank
    @Size(max = 255)
    private String firstName;

    @NotBlank
    @Size(max = 255)
    private String lastName;

    @Size(max = 45)
    private String phone;
}
