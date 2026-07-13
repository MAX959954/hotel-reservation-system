package booking;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BookingResponse {
    private Long Id;
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
    private Double totalPrice;
    private String specialRequest;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
}
