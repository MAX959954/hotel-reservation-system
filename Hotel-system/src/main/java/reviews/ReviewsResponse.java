package reviews;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewsResponse {
    private Long id;
    private Long bookingId;
    private Long userId;
    private String userFullName;
    private Long roomId;
    private String roomNumber;
    private String hotelName;
    private Integer rating;
    private String comment;
    private boolean approved;
    private LocalDateTime createdAt;
    private LocalDateTime stayCheckIn;
    private LocalDateTime stayCheckOut;
}
