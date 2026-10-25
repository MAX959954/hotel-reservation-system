package com.folio.room;

import java.math.BigDecimal;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RoomRequest {

    @NotBlank
    private String number;

    @NotNull
    private RoomType type;

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2)
    private BigDecimal pricePerNight;

    @NotNull
    @Min(1)
    private Integer capacity;

    @NotNull
    private Integer floor;

    private String description;

    @NotNull
    private Long hotelId;
}
