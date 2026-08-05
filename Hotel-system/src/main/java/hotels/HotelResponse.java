package hotels;


import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
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
    private Long companyId;
    private String companyName;
    private Set<Amenity> amenities;
}
