package booking;

import java.time.LocalDateTime;

// Carries everything the status email needs out of the transaction that changed the
// booking — the listener that sends it runs after commit (see BookingServiceImpl), by
// which point the Booking/User/Room/Hotel entities may no longer be attached to an open
// Hibernate session.
record BookingStatusChangedEvent(
        String email,
        String guestName,
        String hotelName,
        String roomNumber,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        BookingStatus status
) {
}
