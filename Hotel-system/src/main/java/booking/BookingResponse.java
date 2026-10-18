package booking;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BookingResponse {
    private Long id;
    private Long userId;
    private String userFullName;
    private Long roomId;
    private String roomNumber;
    private Long hotelId;
    private String hotelName;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private Integer guestCount;
    private BookingStatus bookingStatus;
    private BigDecimal totalPrice;
    private String specialRequest;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    // Cancellation policy as it applies to this booking (see BookingServiceImpl.cancelByGuest):
    // a guest cancelling after freeCancellationUntil forfeits lateCancellationFee.
    private LocalDateTime freeCancellationUntil;
    private BigDecimal lateCancellationFee;
    private LocalDateTime createdAt;
}
