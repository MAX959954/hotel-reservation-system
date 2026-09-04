package booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// Runs independently of any request — nothing else in this app ever cancels a booking
// nobody confirmed, or flags a guest who never showed up; both states were previously
// only reachable by a staff member remembering to do it by hand.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookingLifecycleScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Value("${app.bookings.pending-hold-hours:48}")
    private long pendingHoldHours;

    @Value("${app.bookings.no-show-grace-hours:6}")
    private long noShowGraceHours;

    @Scheduled(cron = "0 */15 * * * *")
    public void cancelAbandonedPendingBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(pendingHoldHours);
        List<Booking> stale = bookingRepository.findStalePending(cutoff);
        for (Booking booking : stale) {
            try {
                bookingService.cancel(booking.getId());
                log.info("Auto-cancelled abandoned PENDING booking {}", booking.getId());
            } catch (Exception e) {
                // One row that changed underneath this job (or a genuinely unexpected
                // error) must not stop the rest of the batch from running.
                log.warn("Could not auto-cancel booking {}: {}", booking.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void markOverdueBookingsAsNoShow() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(noShowGraceHours);
        List<Booking> overdue = bookingRepository.findOverdueForNoShow(cutoff);
        for (Booking booking : overdue) {
            try {
                bookingService.noShow(booking.getId());
                log.info("Marked booking {} as NO_SHOW", booking.getId());
            } catch (Exception e) {
                log.warn("Could not mark booking {} as NO_SHOW: {}", booking.getId(), e.getMessage());
            }
        }
    }
}
