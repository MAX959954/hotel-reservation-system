package user;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
public class LogInRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}
