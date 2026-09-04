package user;

import companyuser.CompanyUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
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
    private final RefreshTokenService refreshTokenService;
    private final JwtBlacklistService jwtBlacklistService;

    @Value("${app.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.login.lockout-minutes:15}")
    private long lockoutMinutes;

    @Override
    public void requestOtp(String identifier) {
        otpService.requestCode(identifier);
    }

    @Override
    @Transactional
    public void login(LoginRequest request) {
        String normalized = request.getIdentifier().trim().toLowerCase();

        // Same generic failure for "no such account", "account has no password" (Google-only
        // sign-up never sets one), "wrong password" AND "temporarily locked out" —
        // confirming which of those is true for an email an attacker doesn't already know
        // is registered (or is currently rate-limited) would itself be an account
        // enumeration/probing leak. This is also why BCrypt's own matches() isn't called on
        // a null hash: it throws on that, which would otherwise 500 instead of failing cleanly.
        User user = userRepository.findByEmail(normalized).orElse(null);

        if (user != null && isLockedOut(user)) {
            throw new IllegalStateException("Incorrect email or password.");
        }

        boolean ok = user != null
                && user.getPasswordHash() != null
                && passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

        if (!ok) {
            if (user != null) {
                registerFailedLogin(user);
            }
            throw new IllegalStateException("Incorrect email or password.");
        }

        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }

        otpService.sendLoginCode(normalized);
    }

    // LOCKED is set only by registerFailedLogin below (never by an admin — see
    // AccountStatus/updateAccountStatus, which uses SUSPENDED/BANNED/DEACTIVATED for
    // that), so it's always safe for this same mechanism to clear it again.
    private boolean isLockedOut(User user) {
        if (user.getAccountStatus() != AccountStatus.LOCKED) {
            return false;
        }
        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(LocalDateTime.now())) {
            // Lockout window has passed — clear it now rather than waiting on some other
            // process to do it, so this very attempt can proceed to the password check.
            user.setAccountStatus(AccountStatus.APPROVED);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            return false;
        }
        return true;
    }

    private void registerFailedLogin(User user) {
        if (user.getAccountStatus() != AccountStatus.APPROVED) {
            // Only ever escalate an account this mechanism itself would later unlock again
            // — never touch a status an admin set for an unrelated reason (BANNED,
            // SUSPENDED, ...), which isLockedOut()'s auto-clear must never undo.
            return;
        }

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedAttempts) {
            user.setAccountStatus(AccountStatus.LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
        }
        userRepository.save(user);
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
        // Every session — including the one making this very call — needs a fresh login
        // after this: an access token issued a moment ago has an `iat` before this
        // instant and gets rejected by JwtAuthFilter's tokenValidAfter check the next
        // time it's used, and every refresh token is revoked outright so none of them
        // can mint a replacement either. A stolen token is worth nothing the moment the
        // real owner changes their password, not just once each old token happens to expire.
        user.setTokenValidAfter(LocalDateTime.now());
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        Long userId = refreshTokenService.validateAndConsume(request.getRefreshToken());
        if (userId == null) {
            throw new IllegalStateException("Refresh token is invalid or expired. Please sign in again.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Account no longer exists."));
        return issueAuthResponse(user);
    }

    @Override
    public void logout(RefreshTokenRequest request, String accessToken) {
        // Both steps are best-effort: a client calling this after its refresh token
        // already expired, or with no Authorization header at all, should still get a
        // clean response rather than an error — there's nothing left to revoke in that
        // case, not a failure.
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            refreshTokenService.validateAndConsume(request.getRefreshToken());
        }
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                jwtBlacklistService.blacklist(jwtService.extractJti(accessToken), jwtService.remainingValidity(accessToken));
            } catch (Exception e) {
                // Malformed, unsigned, or already-expired token — nothing meaningful to
                // blacklist; the request itself is still a successful logout.
            }
        }
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

    @Override
    public List<UserProfileResponse> searchUsers(String search, Roles role, AccountStatus status) {
        String normalizedSearch = (search == null || search.isBlank()) ? "" : search.trim();
        return userRepository.search(normalizedSearch, role, status).stream().map(this::toProfileResponse).toList();
    }

    @Override
    public UserProfileResponse getProfileById(Long userId) {
        return toProfileResponse(findById(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateAccountStatus(Long userId, AccountStatus status) {
        User user = findById(userId);
        user.setAccountStatus(status);
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
        // Password + OTP already got this far, so this isn't the enumeration-sensitive
        // path login() is — a clear reason is fine to surface here.
        if (user.getAccountStatus() != AccountStatus.APPROVED) {
            throw new IllegalStateException("This account is not active. Contact support for help.");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRoles());
        String refreshToken = refreshTokenService.issue(user.getId());
        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
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

        return new CustomUserDetails(user, authorities);
    }
}
