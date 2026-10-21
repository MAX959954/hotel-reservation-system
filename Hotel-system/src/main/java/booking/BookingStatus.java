package booking;

public enum BookingStatus {
    PENDING ,
    CONFIRMED ,
    CHECKED_IN ,
    COMPLETED ,
    CANCELLED ,
    NO_SHOW
    // No PAYMENT_FAILED: a failed card payment is a Payment state (PaymentStatus.FAILED),
    // not a booking state — the guest can retry while the booking stays CONFIRMED, and an
    // unpaid booking is released by BookingLifecycleScheduler.cancelUnpaidConfirmedBookings.
}
