package user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByVerificationToken(String token);

    Optional<User> findByResetPasswordToken(String token);

    boolean existsByEmail(String email);

    List<User> findByAccountStatus(AccountStatus accountStatus);

    List<User> findByRolesContaining(Roles role);

    // Backs the admin users board's search box — matches on email or full name, and
    // combines with the role/status dropdowns via null-means-"don't filter" params
    // rather than needing a separate repository method per filter combination.
    // `search` is compared against '' rather than checked "is null": binding a genuinely
    // null String parameter here makes Postgres unable to infer lower(?)'s argument type
    // (it falls back to bytea and the query 500s with "function lower(bytea) does not
    // exist") — see UserServiceImpl.searchUsers, which normalizes null/blank to "".
    @Query("""
            select u from User u
            where (:search = ''
                   or lower(u.email) like lower(concat('%', :search, '%'))
                   or lower(concat(u.firstName, ' ', u.lastName)) like lower(concat('%', :search, '%')))
              and (:role is null or :role member of u.roles)
              and (:status is null or u.accountStatus = :status)
            order by u.createdAt desc
            """)
    List<User> search(@Param("search") String search, @Param("role") Roles role, @Param("status") AccountStatus status);
}
