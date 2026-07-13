package room;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoomRequest {

    @NotBlank
    private String number;

    @NotNull
    private RoomType type;

    @NotNull
    @Min(0)
    private Double pricePerNight;

    @NotNull
    @Min(1)
    private Integer capacity;

    @NotNull
    private Integer floor;

    private String description;

    @NotNull
    private Long hotelId;
}
