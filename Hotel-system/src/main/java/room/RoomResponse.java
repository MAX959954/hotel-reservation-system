package room;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RoomResponse {
    private Long id;
    private String number;
    private RoomType type;
    private Double pricePerNight;
    private Integer capacity;
    private Integer floor;
    private RoomStatus status;
    private String description;
    private LocalDateTime createdAt;
    private Long hotelId;
    private String hotelName;
}
