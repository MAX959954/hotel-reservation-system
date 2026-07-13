package booking;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingRequest {

    @NotNull
    private Long roomId;

    @NotNull
    private Long userId;

    @NotNull
    @Future
    private LocalDateTime checkIn;

    @NotNull
    @Future
    private LocalDateTime checkOut;

    @NotNull
    @Min(1)
    private Integer guestCount;

    private String specialRequest;
}
