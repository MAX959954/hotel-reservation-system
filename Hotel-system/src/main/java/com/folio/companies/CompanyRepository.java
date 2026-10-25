package com.folio.companies;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByEmail(String email);

    Optional<Company> findByName(String name);

    List<Company> findByStatus (CompanyStatus status);

    List<Company> findByCountry (String country);

    List<Company> findByCity (String city);

    boolean existsByEmail (String email);
}
