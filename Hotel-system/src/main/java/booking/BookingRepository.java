package booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByRoomId(Long roomId);

    List<Booking> findByBookingStatus(BookingStatus status);

    // CANCELLED bookings never occupied the room, so they must not keep blocking those
    // dates for everyone else.
    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.check_in < :checkOut AND b.check_out > :checkIn " +
            "AND b.bookingStatus <> booking.BookingStatus.CANCELLED")
    List<Booking> findOverlappingBookings(@Param("roomId") Long roomId,
                                          @Param("checkIn") LocalDateTime checkIn,
                                          @Param("checkOut") LocalDateTime checkOut);

    List<Booking> findByUserIdAndBookingStatus(Long userId, BookingStatus status);

    // Authorization only needs the owning company's id — one scalar query instead of
    // loading the booking and walking room -> hotel -> company lazily.
    @Query("SELECT b.room.hotel.company.id FROM Booking b WHERE b.id = :id")
    Optional<Long> findCompanyIdById(@Param("id") Long id);

    List<Booking> findByRoom_Hotel_Company_Id(Long companyId);

    List<Booking> findByRoom_Hotel_Company_IdAndBookingStatus(Long companyId, BookingStatus status);

    // Used by BookingLifecycleScheduler to auto-cancel requests staff never acted on —
    // see app.bookings.pending-hold-hours.
    @Query("SELECT b FROM Booking b WHERE b.bookingStatus = booking.BookingStatus.PENDING AND b.created_at < :cutoff")
    List<Booking> findStalePending(@Param("cutoff") LocalDateTime cutoff);

    // Used by BookingLifecycleScheduler to flag guests who never checked in — see
    // app.bookings.no-show-grace-hours.
    @Query("SELECT b FROM Booking b WHERE b.bookingStatus = booking.BookingStatus.CONFIRMED AND b.check_in < :cutoff")
    List<Booking> findOverdueForNoShow(@Param("cutoff") LocalDateTime cutoff);

    // Used by BookingLifecycleScheduler to release rooms held by bookings nobody pays for —
    // see app.bookings.payment-deadline-hours. A PENDING payment counts as "being paid"
    // (a card payment in progress, or cash / bank transfer the guest chose to pay on
    // arrival); a stale card attempt is expired by PaymentLifecycleScheduler first and
    // the booking is picked up here on a later run.
    @Query("SELECT b FROM Booking b WHERE b.bookingStatus = booking.BookingStatus.CONFIRMED AND b.confirmed_at < :cutoff " +
            "AND NOT EXISTS (SELECT p FROM Payment p WHERE p.booking = b AND p.status IN (" +
            "payment.PaymentStatus.PENDING, payment.PaymentStatus.COMPLETED, " +
            "payment.PaymentStatus.PARTIALLY_REFUNDED, payment.PaymentStatus.CHARGEBACK))")
    List<Booking> findConfirmedUnpaidSince(@Param("cutoff") LocalDateTime cutoff);

    // Bookings whose stay hasn't started yet — what has to be cancelled (and refunded)
    // when the hotel or its company stops operating.
    @Query("SELECT b FROM Booking b WHERE b.room.hotel.company.id = :companyId " +
            "AND b.bookingStatus IN (booking.BookingStatus.PENDING, booking.BookingStatus.CONFIRMED)")
    List<Booking> findUpcomingByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT b FROM Booking b WHERE b.room.hotel.id = :hotelId " +
            "AND b.bookingStatus IN (booking.BookingStatus.PENDING, booking.BookingStatus.CONFIRMED)")
    List<Booking> findUpcomingByHotelId(@Param("hotelId") Long hotelId);
}
