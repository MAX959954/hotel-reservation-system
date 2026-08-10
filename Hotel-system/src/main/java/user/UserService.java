package user;

import org.springframework.web.multipart.MultipartFile;

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
}
