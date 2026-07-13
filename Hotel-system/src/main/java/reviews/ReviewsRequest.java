package reviews;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewsRequest {

    @NotNull
    private Long bookingId;

    @NotNull
    @Min(1) @Max(5)
    private Integer rating;

    private String comment;
}
