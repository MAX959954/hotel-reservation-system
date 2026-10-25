package com.folio.room;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
// See HotelResponse: @Builder alone leaves no no-args constructor, which the Redis cache
// deserializer requires.
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {
    private Long id;
    private String number;
    private RoomType type;
    private BigDecimal pricePerNight;
    private Integer capacity;
    private Integer floor;
    private RoomStatus status;
    private String description;
    private LocalDateTime createdAt;
    private Long hotelId;
    private String hotelName;
}
