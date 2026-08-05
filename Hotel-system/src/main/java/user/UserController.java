package user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequestPayload payload) {
        userService.requestOtp(payload.getIdentifier());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<OtpVerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyPayload payload) {
        return ResponseEntity.ok(userService.verifyOtp(payload));
    }

    @PostMapping("/complete-registration")
    public ResponseEntity<AuthResponse> completeRegistration(@Valid @RequestBody CompleteRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.completeRegistration(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(userService.authenticateWithGoogle(request.getIdToken()));
    }

}
