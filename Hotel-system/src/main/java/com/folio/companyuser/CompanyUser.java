package com.folio.companyuser;

import com.folio.companies.Company;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.folio.user.User;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "company_users")
public class CompanyUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_role", nullable = false)
    @Builder.Default
    private CompanyRole companyRole = CompanyRole.STAFF;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CompanyUserStatus status = CompanyUserStatus.INVITED;

    @Column(name = "invited_at", nullable = false)
    @Builder.Default
    private LocalDateTime invitedAt = LocalDateTime.now();

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    // Null until the invited email address belongs to a registered account — see
    // CompanyUserServiceImpl.invite (creates it null for an unregistered email) and
    // .linkPendingInvites (fills it in once that email signs up).
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id")
    private User user;

    // The address an invite was sent to before the invitee had an account. Cleared once
    // `user` is linked; NULL for invites that always had a known userId.
    @Column(name = "invited_email")
    private String invitedEmail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
