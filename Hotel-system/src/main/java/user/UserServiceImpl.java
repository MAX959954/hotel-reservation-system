package user;

import companyuser.CompanyUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.multipart.MultipartFile;

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
    private final AvatarStorageService avatarStorageService;
    private final CompanyUserService companyUserService;

    @Override
    public void requestOtp(String identifier) {
        otpService.requestCode(identifier);
    }

    @Override
    public void login(LoginRequest request) {
        String normalized = request.getIdentifier().trim().toLowerCase();

        // Same generic failure for "no such account", "account has no password" (Google-only
        // sign-up never sets one) and "wrong password" — confirming which of those is true
        // for an email an attacker doesn't already know it's registered would be an account
        // enumeration leak. This is also why BCrypt's own matches() isn't called on a null
        // hash: it throws on that, which would otherwise 500 instead of failing cleanly.
        User user = userRepository.findByEmail(normalized).orElse(null);
        boolean ok = user != null
                && user.getPasswordHash() != null
                && passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

        if (!ok) {
            throw new IllegalStateException("Incorrect email or password.");
        }

        otpService.sendLoginCode(normalized);
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
        companyUserService.linkPendingInvites(user);

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
        // Safe to call unconditionally, not just for brand-new accounts: an email that
        // already resolved to an existing user here was never given an invited_email row
        // in the first place (CompanyUserServiceImpl.invite links straight to that user),
        // so this is a no-op query for the existing-user branch above.
        companyUserService.linkPendingInvites(user);

        return issueAuthResponse(user);
    }

    @Override
    public UserProfileResponse getCurrentProfile() {
        return toProfileResponse(currentUser());
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = currentUser();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        return toProfileResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUser();

        // A hash already on the account must be proven before it's replaced — otherwise a
        // stolen JWT (which normally just expires) could be used to permanently lock the
        // real owner out by setting a new password. An account with no hash yet (signed up
        // via Google, never set one) has nothing to prove, so this step is skipped rather
        // than rejecting everyone who legitimately has no current password.
        if (user.getPasswordHash() != null
                && !passwordEncoder.matches(nullToEmpty(request.getCurrentPassword()), user.getPasswordHash())) {
            throw new IllegalStateException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    @Transactional
    public UserProfileResponse uploadAvatar(MultipartFile file) {
        User user = currentUser();
        String previous = user.getAvatarUrl();

        user.setAvatarUrl(avatarStorageService.store(user.getId(), file));
        userRepository.save(user);

        // Only after the new file and the DB row are both settled — deleting the old one
        // first and then failing to save would leave the account pointing at a file that
        // no longer exists.
        avatarStorageService.deleteIfExists(previous);

        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse grantRole(Long userId, Roles role) {
        User user = findById(userId);
        user.getRoles().add(role);
        return toProfileResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserProfileResponse revokeRole(Long userId, Roles role) {
        User user = findById(userId);
        user.getRoles().remove(role);
        return toProfileResponse(userRepository.save(user));
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found: " + id));
    }

    // Mirrors the "current user from the JWT" pattern already used in BookingServiceImpl:
    // the authenticated principal's name is the email JwtAuthFilter put there, never a
    // client-supplied id — the same reason a booking can't be created on someone else's account.
    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found by that email: " + email));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .dateOfBirth(user.getDateOfBirth())
                .emailVerified(user.isEmailVerified())
                .accountStatus(user.getAccountStatus())
                .roles(user.getRoles())
                .createdAt(user.getCreatedAt())
                .build();
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
