package user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OtpVerifyPayload {

    @NotBlank
    private String identifier;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "must be a 6-digit code")
    private String code;
}
