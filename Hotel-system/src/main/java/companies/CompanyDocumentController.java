package companies;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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

    // The only way to read a document's contents. These files (IDs, business registration)
    // used to be served as public static files under /uploads/documents/**, where anyone
    // holding the link could open them without signing in. Same gate as listing them.
    @GetMapping("/{documentId}/file")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.isCompanySubmitter(#companyId)")
    public ResponseEntity<Resource> download(@PathVariable Long companyId, @PathVariable Long documentId) {
        CompanyDocumentService.Download file = companyDocumentService.download(companyId, documentId);
        String filename = file.filename() != null ? file.filename() : "document";
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(file.resource());
    }
}
