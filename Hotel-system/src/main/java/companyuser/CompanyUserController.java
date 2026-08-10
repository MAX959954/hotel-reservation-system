package companyuser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/company_users")
public class CompanyUserController {

    private final CompanyUserService companyUserService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.hasRole(#request.companyId , 'OWNER' , 'MANAGER')")
    public ResponseEntity<CompanyUserResponse> invite (@Valid @RequestBody CompanyUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyUserService.invite(request));
    }

    // Previously ungated — any authenticated user could accept anyone else's invite by
    // guessing the numeric id. isCompanyUserSelf requires the row's linked user to be
    // the caller (a still-pending row with no user linked matches no one, correctly).
    @PatchMapping("/{id}/accept")
    @PreAuthorize("@companyAuth.isCompanyUserSelf(#id)")
    public ResponseEntity<CompanyUserResponse> acceptInvite(@PathVariable Long id) {
        return ResponseEntity.ok(companyUserService.acceptInvite(id));
    }

    @PostMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.hasRoleForMember(#id , 'OWNER' , 'MANAGER')")
    public ResponseEntity<CompanyUserResponse> changeRole ( @PathVariable Long id, @RequestParam CompanyRole role) {
        return ResponseEntity.ok(companyUserService.changeRole(id , role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.hasRoleForMember(#id , 'OWNER' , 'MANAGER')")
    public  ResponseEntity<Void> remove(@PathVariable Long id) {
        companyUserService.remove(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<CompanyUserResponse>> getByCompany(@PathVariable Long companyId){
        return ResponseEntity.ok(companyUserService.getByCompany(companyId));
    }

    // The manage-bookings panel's first call: which companies (if any) am I staff on,
    // so the frontend knows whether to show it at all and which company to default to.
    @GetMapping("/me")
    public ResponseEntity<List<CompanyUserResponse>> getMine() {
        return ResponseEntity.ok(companyUserService.getMine());
    }

}
