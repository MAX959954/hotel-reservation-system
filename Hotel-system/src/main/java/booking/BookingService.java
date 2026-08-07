package booking;

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

    BookingResponse cancel(Long id);

    BookingResponse checkIn(Long id);

    BookingResponse complete(Long id);

    void delete(Long id );

}
