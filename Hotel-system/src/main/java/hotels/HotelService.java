package hotels;

import java.util.List;

public interface HotelService {

    HotelResponse create(CreateHotelRequest request);

    HotelResponse getById(Long id);

    List<HotelResponse> getByCity(String city);

    List<HotelResponse> getByCountry(String country);

    List<HotelResponse> getByRating(Integer rating);

    List<HotelResponse> getByCompany(Long companyId);

    HotelResponse updateStatus(Long id , Hotel_Status status);

    void delete(Long id);
}
