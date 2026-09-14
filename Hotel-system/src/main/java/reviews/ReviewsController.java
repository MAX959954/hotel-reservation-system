package reviews;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewsController {

    private final ReviewsService reviewsService;

    // Only the guest whose own COMPLETED booking this is can leave a review on it —
    // without this, any authenticated user could post a review against a stranger's
    // booking by guessing its id (the service attaches it to booking.getUser(), not the
    // caller, so it's not impersonation for the caller's own benefit, but it's still
    // unwanted content forced onto someone else's stay).
    @PostMapping
    @PreAuthorize("@companyAuth.isBookingOwner(#request.bookingId)")
    public ResponseEntity<ReviewsResponse> create(@Valid @RequestBody ReviewsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewsService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.isReviewOwner(#id) or @companyAuth.hasRoleForReview(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<ReviewsResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reviewsService.getById(id));
    }

    // Unlike getApprovedByRoom below (the public, browsing-safe view), this includes
    // reviews still pending moderation — only that hotel's own staff/admin should see
    // those before they're public.
    @GetMapping("/room/{roomId}")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.hasRoleForRoom(#roomId , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<List<ReviewsResponse>> getByRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(reviewsService.getByRoom(roomId));
    }

    @GetMapping("/room/{roomId}/approved")
    public ResponseEntity<List<ReviewsResponse>> getApprovedByRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(reviewsService.getApprovedByRoom(roomId));
    }

    @GetMapping("/room/{roomId}/rating")
    public ResponseEntity<Double> getAverageRating(@PathVariable Long roomId) {
        return ResponseEntity.ok(reviewsService.getAverageRatingByRoom(roomId));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.isSelf(#userId)")
    public ResponseEntity<List<ReviewsResponse>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(reviewsService.getByUser(userId));
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.isBookingOwner(#bookingId) or @companyAuth.hasRoleForBooking(#bookingId , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<ReviewsResponse> getByBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(reviewsService.getByBooking(bookingId));
    }

    // Moderation — was completely ungated before (any authenticated user, including the
    // review's own author, could self-approve it), which defeats the point of
    // is_approved existing at all.
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.hasRoleForReview(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<ReviewsResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(reviewsService.approve(id));
    }

    // Was also completely ungated — any authenticated user could delete any review by
    // id (a competitor scrubbing bad reviews off a rival's listing, or plain vandalism).
    // The author may still remove their own; otherwise it's staff of that hotel or admin.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.isReviewOwner(#id) or @companyAuth.hasRoleForReview(#id , 'OWNER' , 'MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewsService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
