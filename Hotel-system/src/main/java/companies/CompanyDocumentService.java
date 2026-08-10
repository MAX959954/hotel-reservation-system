package companies;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CompanyDocumentService {
    CompanyDocumentResponse upload(Long companyId, MultipartFile file);
    List<CompanyDocumentResponse> getByCompany(Long companyId);
}
