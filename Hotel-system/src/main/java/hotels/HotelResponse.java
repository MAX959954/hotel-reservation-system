package hotels;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
// Both constructors are required for the Redis cache: @Builder alone leaves the class with
// only an all-args constructor, and Jackson cannot rebuild a cached entry without a
// no-args one ("no Creators, like default constructor, exist").
@NoArgsConstructor
@AllArgsConstructor
public class HotelResponse {

    private Long id;
    private String name;
    private String city;
    private String country;
    private String address;
    private Integer startRating;
    private String phone;
    private String email;
    private String description;
    private String imageUrl;
    private Hotel_Status status;
    private PropertyType propertyType;
    private Long companyId;
    private String companyName;
    private Set<Amenity> amenities;
}
