package user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Shared shape for POST /api/auth/refresh and POST /api/auth/logout — both act on one
 *  refresh token, just in opposite directions (mint a new one / revoke it). */
@Data
public class RefreshTokenRequest {

    @NotBlank
    private String refreshToken;
}
