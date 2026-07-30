package user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

// Postgres advisory lock scoped to the current transaction: serializes concurrent OTP
// requests for the same identifier so OtpService's count-then-insert rate check can't
// race across two requests hitting requestCode at once. Released automatically when the
// caller's transaction commits or rolls back — must be called from within one.
@Component
public class OtpRateLimitLock {

    @PersistenceContext
    private EntityManager entityManager;

    public void acquire(String identifier) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:identifier))")
                .setParameter("identifier", identifier)
                .getSingleResult();
    }
}
