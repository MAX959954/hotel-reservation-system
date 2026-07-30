package user;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OtpVerifyResponse {

    private boolean newAccount;

    /** Present when {@code newAccount} is true — pass to /complete-registration. */
    private String verificationTicket;

    /** Present when {@code newAccount} is false — the caller is already logged in. */
    private AuthResponse auth;
}
