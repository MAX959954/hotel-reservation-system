package user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;



@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy =  GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firstName" , nullable = false)
    private String firstName;

    @Column(name = "lastName" , nullable = false)
    private String lastName;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "email" , nullable = false , length = 255)
    private String email;

    @Column(name = "phone" , length = 45)
    private String phone;

    @Column(name = "avatar_url" , length = 500)
    private String avatarUrl;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "user_roles" ,
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    @Builder.Default
    private Set<Roles> roles = new HashSet<>();


    @Builder.Default
    private boolean emailVerified = false;
    private String verificationToken;

    private String resetPasswordToken;
    private LocalDateTime resetTokenExpiry;

    @Builder.Default
    private boolean enabled = false;

    // APPROVED, not PENDING: both signup paths (OTP + Google) verify the email before an
    // account exists, so there's no separate review step to wait on — see AccountStatus.
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.APPROVED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate () {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate () {
        updatedAt = LocalDateTime.now();
    }
}
