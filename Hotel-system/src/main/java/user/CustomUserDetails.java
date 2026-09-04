package user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Wraps the domain User instead of building a plain Spring Security User — JwtAuthFilter
 * needs {@link #getTokenValidAfter()} to reject a token issued before the account's last
 * forced-logout instant (password change), which the standard User type has no field for.
 */
class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final boolean emailVerified;
    private final boolean approved;
    private final LocalDateTime tokenValidAfter;
    private final Collection<? extends GrantedAuthority> authorities;

    CustomUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash() == null ? "" : user.getPasswordHash();
        this.emailVerified = user.isEmailVerified();
        // Allowlist, not a denylist: only APPROVED can authenticate. A denylist of just
        // LOCKED/BANNED would silently let SUSPENDED, DEACTIVATED, REJECTED and
        // ANONYMIZED accounts log in, since nothing else in the codebase blocks them.
        this.approved = user.getAccountStatus() == AccountStatus.APPROVED;
        this.tokenValidAfter = user.getTokenValidAfter();
        this.authorities = authorities;
    }

    Long getUserId() {
        return userId;
    }

    LocalDateTime getTokenValidAfter() {
        return tokenValidAfter;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // Reusing "locked" for accountStatus == APPROVED, same mapping loadUserByUsername
    // used before this field existed anywhere that actually checked it — see the
    // approved field's own comment for why this must be an allowlist.
    @Override
    public boolean isAccountNonLocked() {
        return approved;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return emailVerified;
    }
}
