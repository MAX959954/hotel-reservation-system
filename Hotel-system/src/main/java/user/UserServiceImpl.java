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

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email already in use : " + request.getEmail());
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new IllegalStateException("Phone already in use : " + request.getPhone());
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password_hash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .enabled(true)
                .accountStatus(AccountStatus.PENDING)
                .build();

        userRepository.save(user);

        String token  = jwtService.generateToken(user.getEmail() , user.getRoles());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();
    }

    @Override
    public AuthResponse logIn(LogInRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User not found " + request.getEmail()));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword_hash())){
            throw new IllegalStateException("Invalid password");
        }

        String token = jwtService.generateToken(user.getEmail() , user.getRoles());

        return AuthResponse.builder()
                .token(token)
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
                user.getPassword_hash(),
                user.isEmailVerified(),
                true ,
                true ,
                user.getAccountStatus() != AccountStatus.LOCKED && user.getAccountStatus() != AccountStatus.BANNED,
                authorities
        );
    }
}
