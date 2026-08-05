package user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements  UserService , UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Override
    public void requestOtp(String identifier) {
        otpService.requestCode(identifier);
    }

    @Override
    @Transactional
    public OtpVerifyResponse verifyOtp(OtpVerifyPayload payload) {
        String identifier = otpService.verifyCode(payload.getIdentifier(), payload.getCode());

        return userRepository.findByEmail(identifier)
                .map(user -> OtpVerifyResponse.builder()
                        .newAccount(false)
                        .auth(issueAuthResponse(user))
                        .build())
                .orElseGet(() -> OtpVerifyResponse.builder()
                        .newAccount(true)
                        .verificationTicket(jwtService.generateOtpTicket(identifier))
                        .build());
    }

    @Override
    @Transactional
    public AuthResponse completeRegistration(CompleteRegistrationRequest request) {
        String email = otpService.resolveVerifiedIdentifier(request.getVerificationTicket());

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("An account for this address already exists — log in instead.");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .dateOfBirth(request.getDateOfBirth())
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .enabled(true)
                .accountStatus(AccountStatus.APPROVED)
                .build();

        userRepository.save(user);

        return issueAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse authenticateWithGoogle(String idToken) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(idToken);

        User user = userRepository.findByGoogleId(identity.getSubject())
                .or(() -> userRepository.findByEmail(identity.getEmail()))
                .orElseGet(() -> User.builder()
                        .firstName(identity.getFirstName().isBlank() ? "Guest" : identity.getFirstName())
                        .lastName(identity.getLastName())
                        .email(identity.getEmail())
                        .roles(Set.of(Roles.GUEST))
                        .emailVerified(true)
                        .enabled(true)
                        .accountStatus(AccountStatus.APPROVED)
                        .build());

        user.setGoogleId(identity.getSubject());
        userRepository.save(user);

        return issueAuthResponse(user);
    }

    private AuthResponse issueAuthResponse(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getRoles());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found by that email: " + email));

        var authorities = user.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).collect(Collectors.toSet());

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash() == null ? "" : user.getPasswordHash(),
                user.isEmailVerified(),
                true ,
                true ,
                // Allowlist, not a denylist: only APPROVED can authenticate. A denylist of just
                // LOCKED/BANNED would silently let SUSPENDED, DEACTIVATED, REJECTED and
                // ANONYMIZED accounts log in, since nothing else in the codebase blocks them.
                user.getAccountStatus() == AccountStatus.APPROVED,
                authorities
        );
    }
}
