package booking;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN' , 'RECEPTIONIST') or @companyAuth.hasRoleForBooking(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST') or @companyAuth.isBookingOwner(#id)")
    public ResponseEntity<BookingResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getById(id));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN' ) or @companyAuth.isSelf(#userId)")
    public ResponseEntity<List<BookingResponse>> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(bookingService.getByUserId(userId));
    }

    @GetMapping("/room/{roomId}")
    @PreAuthorize("hasAnyRole('ADMIN' , 'RECEPTIONIST') or @companyAuth.hasRoleForRoom(#roomId , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<List<BookingResponse>> getByRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(bookingService.getByRoom(roomId));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<List<BookingResponse>> getByStatus(@PathVariable BookingStatus status) {
        return ResponseEntity.ok(bookingService.getByStatus(status));
    }

    // The owner/manager panel's entry point: every booking across a company's hotels,
    // optionally narrowed to one status (e.g. the PENDING queue that needs confirming).
    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN' , 'RECEPTIONIST' , 'HOTEL_MANAGER') or @companyAuth.hasRole(#companyId , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<List<BookingResponse>> getByCompany(@PathVariable Long companyId,
                                                               @RequestParam(required = false) BookingStatus status) {
        return ResponseEntity.ok(bookingService.getByCompany(companyId, status));
    }

    @GetMapping("/user/{userId}/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN') or @companyAuth.isSelf(#userId)")
    public ResponseEntity<List<BookingResponse>> getByUserAndStatus(@PathVariable Long userId, @PathVariable BookingStatus status) {
        return ResponseEntity.ok(bookingService.getByUserAndStatus(userId, status));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN' , 'RECEPTIONIST' , 'HOTEL_MANAGER') or @companyAuth.hasRoleForBooking(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.confirm(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN' , 'RECEPTIONIST' , 'HOTEL_MANAGER') or @companyAuth.hasRoleForBooking(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST') or @companyAuth.isBookingOwner(#id)")
    public ResponseEntity<BookingResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancel(id));
    }

    @PatchMapping("/{id}/checkIn")
    @PreAuthorize("hasAnyRole('ADMIN' , 'RECEPTIONIST' , 'HOTEL_MANAGER') or @companyAuth.hasRoleForBooking(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> checkIn(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.checkIn(id));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN' , 'RECEPTIONIST' , 'HOTEL_MANAGER') or @companyAuth.hasRoleForBooking(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.complete(id));
    }

    // Normally set automatically by BookingLifecycleScheduler once check-in time plus a
    // grace period has passed — exposed here too so front desk can flag it immediately
    // instead of waiting for the next run.
    @PatchMapping("/{id}/noShow")
    @PreAuthorize("hasAnyRole('ADMIN' , 'RECEPTIONIST' , 'HOTEL_MANAGER') or @companyAuth.hasRoleForBooking(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> noShow(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.noShow(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN') or @companyAuth.hasRoleForBooking(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookingService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
