package user;

public interface UserService {

    void requestOtp(String identifier);

    OtpVerifyResponse verifyOtp(OtpVerifyPayload payload);

    AuthResponse completeRegistration(CompleteRegistrationRequest request);

    AuthResponse authenticateWithGoogle(String idToken);
}
