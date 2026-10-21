-- 1. BookingStatus.PAYMENT_FAILED was never assigned by the application: a declined card
-- only marks the Payment FAILED, and the guest can retry while the booking stays
-- CONFIRMED (unpaid bookings are released by BookingLifecycleScheduler instead). The dead
-- state is removed from the enum, so it is removed from the allowed values here too.
-- Any row that somehow carries it never held the room, which is what CANCELLED means.

UPDATE bookings
SET booking_status = 'CANCELLED'
WHERE booking_status = 'PAYMENT_FAILED';

ALTER TABLE bookings DROP CONSTRAINT bookings_booking_status_check;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_booking_status_check
        CHECK (booking_status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW'));

-- 2. One review per booking was only checked in Java (check-then-insert), so two
-- concurrent submissions could both pass. Existing duplicates keep the earliest review.

DELETE FROM reviews r
USING reviews earlier
WHERE r.booking_id = earlier.booking_id
  AND r.id > earlier.id;

ALTER TABLE reviews
    ADD CONSTRAINT reviews_booking_id_key UNIQUE (booking_id);
