package user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByIdentifierAndConsumedFalseOrderByCreatedAtDesc(String identifier);

    // PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) so two concurrent requestCode calls for the
    // same identifier can't both read a count below the limit before either inserts — the
    // second caller blocks here until the first's transaction commits or rolls back. Only
    // guards identifiers that already have at least one row in the window; a truly-first-ever
    // burst for a brand-new identifier has nothing to lock, which is an accepted, narrow gap.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OtpCode> findByIdentifierAndCreatedAtAfterOrderByCreatedAtAsc(String identifier, LocalDateTime after);
}
