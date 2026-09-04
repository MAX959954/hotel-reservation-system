package booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingLifecycleSchedulerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        // @Value fields aren't populated by @InjectMocks (no Spring context here) — set
        // directly like the framework would at runtime.
        ReflectionTestUtils.setField(scheduler, "pendingHoldHours", 48L);
        ReflectionTestUtils.setField(scheduler, "noShowGraceHours", 6L);
    }

    private Booking bookingWithId(Long id) {
        return Booking.builder().id(id).build();
    }

    @Test
    void cancelAbandonedPendingBookings_cancelsEveryStaleBooking() {
        given(bookingRepository.findStalePending(any())).willReturn(List.of(bookingWithId(1L), bookingWithId(2L)));

        scheduler.cancelAbandonedPendingBookings();

        verify(bookingService).cancel(1L);
        verify(bookingService).cancel(2L);
    }

    @Test
    void cancelAbandonedPendingBookings_continuesPastAFailure() {
        given(bookingRepository.findStalePending(any())).willReturn(List.of(bookingWithId(1L), bookingWithId(2L)));
        doThrow(new IllegalStateException("already cancelled by someone else")).when(bookingService).cancel(1L);

        scheduler.cancelAbandonedPendingBookings();

        // The failure on booking 1 must not stop booking 2 from being processed.
        verify(bookingService).cancel(2L);
    }

    @Test
    void cancelAbandonedPendingBookings_doesNothing_whenNoneAreStale() {
        given(bookingRepository.findStalePending(any())).willReturn(List.of());

        scheduler.cancelAbandonedPendingBookings();

        verify(bookingService, never()).cancel(any());
    }

    @Test
    void markOverdueBookingsAsNoShow_flagsEveryOverdueBooking() {
        given(bookingRepository.findOverdueForNoShow(any())).willReturn(List.of(bookingWithId(3L), bookingWithId(4L)));

        scheduler.markOverdueBookingsAsNoShow();

        verify(bookingService).noShow(3L);
        verify(bookingService).noShow(4L);
    }

    @Test
    void markOverdueBookingsAsNoShow_continuesPastAFailure() {
        given(bookingRepository.findOverdueForNoShow(any())).willReturn(List.of(bookingWithId(3L), bookingWithId(4L)));
        doThrow(new IllegalStateException("boom")).when(bookingService).noShow(3L);

        scheduler.markOverdueBookingsAsNoShow();

        verify(bookingService).noShow(4L);
    }
}
