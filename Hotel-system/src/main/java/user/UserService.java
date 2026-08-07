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
}
