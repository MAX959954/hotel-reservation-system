package company_user;

import companies.Companies;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import payment.PaymentStatus;
import user.User;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "company_users")
public class Company_users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status" ,nullable = false)
    @Builder.Default
    private Company_roles role = Company_roles.STAFF;

    @Column(name = "invited_at" ,nullable = false)
    private LocalDateTime invited_at = LocalDateTime.now();

    @Column(name = "joined_at" ,nullable = false)
    private LocalDateTime joined_at = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status" ,nullable = false)
    @Builder.Default
    private Company_roles company_role = Company_roles.STAFF;

    @ManyToOne(fetch =  FetchType.LAZY , optional = false)
    @JoinColumn(name = "user_id" , nullable = false)
    private User user_id;

    @ManyToOne(fetch =  FetchType.LAZY , optional = false)
    @JoinColumn(name = "comnpamy_id" , nullable = false)
    private Companies company_id;
}


