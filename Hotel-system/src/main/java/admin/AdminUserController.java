package admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import user.AccountStatus;
import user.Roles;
import user.UserProfileResponse;
import user.UserService;

import java.util.List;

/** Admin user-management surface backing the users board: search/list, per-user detail,
 *  account-status control, and role grants. Company moderation itself is a separate
 *  concern that already lives on companies.CompaniesController. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserProfileResponse>> search(@RequestParam(required = false) String search,
                                                              @RequestParam(required = false) Roles role,
                                                              @RequestParam(required = false) AccountStatus status) {
        return ResponseEntity.ok(userService.searchUsers(search, role, status));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getProfileById(userId));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserProfileResponse> updateStatus(@PathVariable Long userId,
                                                              @RequestParam AccountStatus status) {
        return ResponseEntity.ok(userService.updateAccountStatus(userId, status));
    }

    @PostMapping("/{userId}/roles/{role}")
    public ResponseEntity<UserProfileResponse> grant(@PathVariable Long userId, @PathVariable Roles role) {
        return ResponseEntity.ok(userService.grantRole(userId, role));
    }

    @DeleteMapping("/{userId}/roles/{role}")
    public ResponseEntity<UserProfileResponse> revoke(@PathVariable Long userId, @PathVariable Roles role) {
        return ResponseEntity.ok(userService.revokeRole(userId, role));
    }
}
