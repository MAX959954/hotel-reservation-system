package user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    @Email(message = "Enter a valid email address")
    private String identifier;

    @NotBlank
    private String password;
}
