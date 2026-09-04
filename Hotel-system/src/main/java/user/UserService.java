package user;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    void requestOtp(String identifier);

    /** Password is the first factor; on success this sends the second-factor email code. */
    void login(LoginRequest request);

    OtpVerifyResponse verifyOtp(OtpVerifyPayload payload);

    AuthResponse completeRegistration(CompleteRegistrationRequest request);

    AuthResponse authenticateWithGoogle(String idToken);

    UserProfileResponse getCurrentProfile();

    UserProfileResponse updateProfile(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);

    UserProfileResponse uploadAvatar(MultipartFile file);

    /** ADMIN-only — see admin.AdminUserController. Granular add/remove rather than a
     *  full-set replace, so a call can't accidentally wipe GUEST or any other role. */
    UserProfileResponse grantRole(Long userId, Roles role);

    UserProfileResponse revokeRole(Long userId, Roles role);

    /** ADMIN-only — the users board's search box + role/status filters. Any argument
     *  left null is not filtered on. */
    List<UserProfileResponse> searchUsers(String search, Roles role, AccountStatus status);

    /** ADMIN-only — any user's profile, not just the caller's own (see AccountController
     *  for that). Backs the users board's detail page. */
    UserProfileResponse getProfileById(Long userId);

    /** ADMIN-only account status transition (e.g. suspending or reinstating an account). */
    UserProfileResponse updateAccountStatus(Long userId, AccountStatus status);
}
