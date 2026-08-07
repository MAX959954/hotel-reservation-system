package hotels;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class CreateHotelRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String city;

    @NotBlank
    private String country;

    @NotBlank
    private String address;

    @Min(1) @Max(5)
    private Integer rating;

    @NotBlank
    private String phone;

    @Email
    @NotBlank
    private String email ;

    @NotBlank
    private String description;

    @NotBlank
    private String imageUrl;

    @NotNull
    private Long companyId;

    /** Defaults to HOTEL in the service layer when omitted — see HotelServiceImpl.create. */
    private PropertyType propertyType;

    private Set<Amenity> amenities;
}
