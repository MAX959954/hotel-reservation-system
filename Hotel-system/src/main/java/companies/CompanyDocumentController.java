package companies;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/documents")
@RequiredArgsConstructor
public class CompanyDocumentController {

    private final CompanyDocumentService companyDocumentService;

    // Only the applicant themselves (while still under review) or an admin can attach
    // supporting documents — not staff of some unrelated company.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.isCompanySubmitter(#companyId)")
    public ResponseEntity<CompanyDocumentResponse> upload(@PathVariable Long companyId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyDocumentService.upload(companyId, file));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.isCompanySubmitter(#companyId)")
    public ResponseEntity<List<CompanyDocumentResponse>> getByCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(companyDocumentService.getByCompany(companyId));
    }
}
