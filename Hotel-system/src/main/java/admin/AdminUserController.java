package admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import user.Roles;
import user.UserProfileResponse;
import user.UserService;

/** First controller in this package — the rest of the eventual admin panel (company
 *  moderation is already on companies.CompaniesController, this is just role grants). */
@RestController
@RequestMapping("/api/admin/users/{userId}/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @PostMapping("/{role}")
    public ResponseEntity<UserProfileResponse> grant(@PathVariable Long userId, @PathVariable Roles role) {
        return ResponseEntity.ok(userService.grantRole(userId, role));
    }

    @DeleteMapping("/{role}")
    public ResponseEntity<UserProfileResponse> revoke(@PathVariable Long userId, @PathVariable Roles role) {
        return ResponseEntity.ok(userService.revokeRole(userId, role));
    }
}
