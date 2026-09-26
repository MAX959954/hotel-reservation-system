package companies;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CompanyDocumentService {
    CompanyDocumentResponse upload(Long companyId, MultipartFile file);
    List<CompanyDocumentResponse> getByCompany(Long companyId);

    /** The document's file, for the authorized download endpoint. The document must
     *  belong to {@code companyId} (the id the caller was authorized against). */
    Download download(Long companyId, Long documentId);

    record Download(org.springframework.core.io.Resource resource, String filename,
                    org.springframework.http.MediaType contentType) {}
}
