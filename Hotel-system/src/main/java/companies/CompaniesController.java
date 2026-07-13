package companies;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompaniesController {

    private final CompaniesService companiesService;

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

    @PatchMapping("/{id}/status")
    public ResponseEntity<CompaniesResponse> updateStatus(@PathVariable Long id, @RequestParam CompaniesStatus status) {
        return ResponseEntity.ok(companiesService.updateStatus(id, status));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompaniesResponse> update(@PathVariable Long id, @Valid @RequestBody CompaniesRequest request) {
        return ResponseEntity.ok(companiesService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        companiesService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
