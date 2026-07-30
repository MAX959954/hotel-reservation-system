package user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompleteRegistrationRequest {

    @NotBlank
    private String verificationTicket;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotNull
    @Past(message = "must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
