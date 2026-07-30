package user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpRequestPayload {

    @NotBlank
    @Email(message = "Enter a valid email address")
    private String identifier;
}
