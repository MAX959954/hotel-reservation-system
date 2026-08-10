package companies;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompaniesController {

    private final CompaniesService companiesService;

    // Open to any authenticated user on purpose — this is the "become a host" application
    // submission, not a privileged action. It lands in PENDING_VERIFICATION regardless of
    // who calls it; approve()/reject() below are what's actually gated.
    @PostMapping
    public ResponseEntity<CompaniesResponse> create(@Valid @RequestBody CompaniesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companiesService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompaniesResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(companiesService.getById(id));
    }

    @GetMapping("/email")
    public ResponseEntity<CompaniesResponse> getByEmail(@RequestParam String email) {
        return ResponseEntity.ok(companiesService.getByEmail(email));
    }

    @GetMapping("/country/{country}")
    public ResponseEntity<List<CompaniesResponse>> getByCountry(@PathVariable String country) {
        return ResponseEntity.ok(companiesService.getByCountry(country));
    }

    @GetMapping("/city/{city}")
    public ResponseEntity<List<CompaniesResponse>> getByCity(@PathVariable String city) {
        return ResponseEntity.ok(companiesService.getByCity(city));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<CompaniesResponse>> getByStatus(@PathVariable CompaniesStatus status) {
        return ResponseEntity.ok(companiesService.getByStatus(status));
    }

    // For transitions other than the initial PENDING_VERIFICATION decision (e.g.
    // ACTIVE -> SUSPENDED) — deliberately does NOT touch roles or CompanyUser membership,
    // only approve()/reject() below do that side effect, and only out of PENDING_VERIFICATION.
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompaniesResponse> updateStatus(@PathVariable Long id, @RequestParam CompaniesStatus status) {
        return ResponseEntity.ok(companiesService.updateStatus(id, status));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompaniesResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(companiesService.approve(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompaniesResponse> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(companiesService.reject(id, reason));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompaniesResponse> update(@PathVariable Long id, @Valid @RequestBody CompaniesRequest request) {
        return ResponseEntity.ok(companiesService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        companiesService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
