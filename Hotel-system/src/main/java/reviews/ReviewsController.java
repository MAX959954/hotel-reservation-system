package reviews;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewsController {

    private final ReviewsService reviewsService;

    @PostMapping
    public ResponseEntity<ReviewsResponse> create(@Valid @RequestBody ReviewsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewsService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewsResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reviewsService.getById(id));
    }

    @GetMapping("/room/{roomId}")
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
    public ResponseEntity<List<ReviewsResponse>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(reviewsService.getByUser(userId));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ReviewsResponse> getByBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(reviewsService.getByBooking(bookingId));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ReviewsResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(reviewsService.approve(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewsService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
