package companies;

import exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyDocumentServiceImpl implements CompanyDocumentService {

    private final CompanyDocumentRepository companyDocumentRepository;
    private final CompaniesRepository companiesRepository;
    private final CompanyDocumentStorageService storageService;

    @Override
    @Transactional
    public CompanyDocumentResponse upload(Long companyId, MultipartFile file) {
        Companies company = companiesRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("Company not found: " + companyId));

        if (company.getStatus() != CompaniesStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Documents can only be added while the application is pending review");
        }

        String url = storageService.store(companyId, file);
        CompanyDocument document = CompanyDocument.builder()
                .companyId(companyId)
                .fileUrl(url)
                .originalFilename(file.getOriginalFilename())
                .build();

        return toResponse(companyDocumentRepository.save(document));
    }

    @Override
    public List<CompanyDocumentResponse> getByCompany(Long companyId) {
        return companyDocumentRepository.findByCompanyId(companyId).stream().map(this::toResponse).toList();
    }

    @Override
    public Download download(Long companyId, Long documentId) {
        CompanyDocument document = companyDocumentRepository.findById(documentId)
                // Also "not found" when it belongs to a different company: the caller was
                // only authorized for companyId, so that must not leak another's file.
                .filter(d -> d.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        String stored = document.getFileUrl();
        MediaType type = stored.endsWith(".pdf") ? MediaType.APPLICATION_PDF
                : stored.endsWith(".png") ? MediaType.IMAGE_PNG
                : stored.endsWith(".jpg") ? MediaType.IMAGE_JPEG
                : MediaType.APPLICATION_OCTET_STREAM;
        return new Download(storageService.load(stored), document.getOriginalFilename(), type);
    }

    private CompanyDocumentResponse toResponse(CompanyDocument document) {
        return CompanyDocumentResponse.builder()
                .id(document.getId())
                .companyId(document.getCompanyId())
                // The authorized download endpoint, not the on-disk path — documents are
                // no longer served as public static files.
                .fileUrl("/api/companies/" + document.getCompanyId() + "/documents/" + document.getId() + "/file")
                .originalFilename(document.getOriginalFilename())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
