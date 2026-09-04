package user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    // First factor of sign-in. On success this sends the same kind of code /otp/request
    // does — the client continues at POST /otp/verify exactly as registration does, since
    // OtpService.sendLoginCode writes the same OtpCode rows requestCode does.
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
        userService.login(request);
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

    // Access tokens are short-lived on purpose (see jwt.expiration) — this is what keeps
    // a session alive past that without asking for the password again. Rotates the
    // refresh token too: the one in the request body is consumed and can't be replayed.
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(userService.refresh(request));
    }

    // No @PreAuthorize / SecurityConfig change needed — this path is already under
    // /api/auth/**'s permitAll, and revocation is keyed off the refresh token in the
    // body plus whatever Authorization header happens to be present, not an
    // authenticated principal. Works even if the access token already expired.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request,
                                        @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String accessToken = (authorizationHeader != null && authorizationHeader.startsWith("Bearer "))
                ? authorizationHeader.substring(7)
                : null;
        userService.logout(request, accessToken);
        return ResponseEntity.noContent().build();
    }

}
