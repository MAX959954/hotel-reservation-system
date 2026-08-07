package user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    /**
     * Not validated with @NotBlank: a Google-only account has no password yet, so this is
     * legitimately absent the first time it sets one. UserServiceImpl.changePassword
     * enforces it whenever a hash already exists.
     */
    private String currentPassword;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
