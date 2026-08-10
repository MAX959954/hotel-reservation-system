package payment;

import java.time.LocalDateTime;

// Carries everything the confirmation email needs out of the transaction that completed
// the payment — the listener that sends it runs after commit (see PaymentServiceImpl),
// by which point the Payment/Booking/User/Room/Hotel entities may no longer be attached
// to an open Hibernate session.
record PaymentCompletedEvent(
        String email,
        String guestName,
        String hotelName,
        String roomNumber,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        double amount,
        String currency
) {
}
