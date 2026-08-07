package user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The signed-in user's own profile. Unlike /api/auth, every route here requires a JWT —
 * covered by SecurityConfig's default `anyRequest().authenticated()`, so no extra
 * permitAll wiring was needed to lock it down.
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile() {
        return ResponseEntity.ok(userService.getCurrentProfile());
    }

    @PatchMapping
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadAvatar(file));
    }
}
