package companies;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyDocumentRepository extends JpaRepository<CompanyDocument, Long> {
    List<CompanyDocument> findByCompanyId(Long companyId);
}
