package user;

import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

@Data
@Builder
public class AuthResponse {

    private String token;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";
    private Long userId;
    private String email;
    private Set<Roles> roles;
}
