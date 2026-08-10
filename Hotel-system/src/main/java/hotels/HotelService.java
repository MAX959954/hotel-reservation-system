package hotels;

import java.util.List;

public interface HotelService {

    HotelResponse create(CreateHotelRequest request);

    /** @param locale drives which hotel_translations row backs the description — "en" or
     *  null/blank always means the base hotels.description column. */
    HotelResponse getById(Long id, String locale);

    List<HotelResponse> getByCity(String city, String locale);

    List<HotelResponse> getByCountry(String country, String locale);

    List<HotelResponse> getByRating(Integer rating, String locale);

    List<HotelResponse> getByCompany(Long companyId, String locale);

    List<HotelResponse> getByPropertyType(PropertyType propertyType, String locale);

    HotelResponse updateStatus(Long id , Hotel_Status status);

    void delete(Long id);
}
