package com.folio.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

public interface BookingService {
    BookingResponse create(BookingRequest requst);

    BookingResponse getById(Long id);

    List<BookingResponse> getByUserId (Long userId);

    List<BookingResponse> getByRoom(Long roomId);

    List<BookingResponse> getByStatus(BookingStatus status);

    List<BookingResponse> getByUserAndStatus(Long userId , BookingStatus status);

    /** @param status null returns every booking for the company, not just one status. */
    List<BookingResponse> getByCompany(Long companyId , BookingStatus status);

    BookingResponse confirm(Long id);

    /** Cancellation by the hotel, an admin or the system: any payment is refunded in full. */
    BookingResponse cancel(Long id);

    /** Cancellation by the guest: applies the cancellation policy — free until
     *  app.bookings.free-cancellation-hours before check-in, after that the hotel keeps
     *  the late-cancellation fee and refunds the rest. */
    BookingResponse cancelByGuest(Long id);

    BookingResponse checkIn(Long id);

    BookingResponse complete(Long id);

    /** Flags a CONFIRMED booking whose guest never checked in. Called manually by staff,
     *  or automatically by BookingLifecycleScheduler once check-in time plus a grace
     *  period has passed. */
    BookingResponse noShow(Long id);

    /** Cancels, with a full refund, every PENDING/CONFIRMED booking of this company's
     *  hotels. Called when the company stops operating (suspended, closed, ...) — its
     *  staff lose access then, so nobody else would ever handle these bookings.
     *  @return how many bookings were cancelled. */
    int cancelUpcomingForCompany(Long companyId);

    /** Same as {@link #cancelUpcomingForCompany} for a single hotel that closes. */
    int cancelUpcomingForHotel(Long hotelId);

    void delete(Long id );

}
