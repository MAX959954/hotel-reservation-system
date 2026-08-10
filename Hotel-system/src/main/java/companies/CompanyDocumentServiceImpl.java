package companies;

import lombok.RequiredArgsConstructor;
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

    private CompanyDocumentResponse toResponse(CompanyDocument document) {
        return CompanyDocumentResponse.builder()
                .id(document.getId())
                .companyId(document.getCompanyId())
                .fileUrl(document.getFileUrl())
                .originalFilename(document.getOriginalFilename())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
