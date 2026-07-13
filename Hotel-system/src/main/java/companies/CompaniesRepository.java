package companies;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompaniesRepository extends JpaRepository<Companies, Long> {

    Optional<Companies> findByEmail(String email);

    Optional<Companies> findByName(String name);

    List<Companies> findByStatus (CompaniesStatus status);

    List<Companies> findByCountry (String country);

    List<Companies> findByCity (String city);

    boolean existsByEmail (String email);
}
