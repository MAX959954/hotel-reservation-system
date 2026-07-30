package user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByIdentifierAndConsumedFalseOrderByCreatedAtDesc(String identifier);

    List<OtpCode> findByIdentifierAndCreatedAtAfter(String identifier, LocalDateTime after);
}
