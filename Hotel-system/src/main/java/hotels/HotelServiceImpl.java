package hotels;

import companies.Companies;
import companies.CompaniesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService{

    private final HotelsRepository hotelsRepository;
    private final CompaniesRepository companiesRepository;

    @Override
    @Transactional
    public  HotelResponse create(CreateHotelRequest request) {
        Companies company = companiesRepository.findById(request.getCompanyId()).orElseThrow(() -> new IllegalStateException("Company not found"+ request.getCompanyId()));

        Hotels hotel = Hotels.builder()
                .name(request.getName())
                .city(request.getCity())
                .country(request.getCountry())
                .address(request.getAddress())
                .star_rating(request.getRating())
                .phone(request.getPhone())
                .email(request.getEmail())
                .description(request.getDescription())
                .image_url(request.getImageUrl())
                .company(company)
                .amenities(request.getAmenities() != null ? request.getAmenities() : new HashSet<>())
                .build();

        return toResponse(hotelsRepository.save(hotel));
    }

    @Override
    public HotelResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public List<HotelResponse> getByCity(String city) {
        return hotelsRepository.findByCity(city).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<HotelResponse> getByCountry(String country) {
        return hotelsRepository.findByCountry(country).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<HotelResponse> getByCompany(Long companyId) {
        return hotelsRepository.findByCompanyId(companyId).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<HotelResponse> getByRating(Integer rating) {
        return hotelsRepository.findByStar_rating(rating).stream().map(this :: toResponse).toList();
    }

    @Override
    @Transactional
    public HotelResponse updateStatus(Long id , Hotel_Status status) {
        Hotels hotel =  findById(id);
        hotel.setStatus(status);
        return toResponse(hotelsRepository.save(hotel));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        hotelsRepository.delete(findById(id));
    }

    private Hotels findById(Long id) {
        return hotelsRepository.findById(id).orElseThrow(() -> new IllegalStateException("Hotel not found " + id));
    }

    private HotelResponse toResponse(Hotels  hotel) {
        return HotelResponse.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .city(hotel.getCity())
                .country(hotel.getCountry())
                .address(hotel.getAddress())
                .startRating(hotel.getStar_rating())
                .phone(hotel.getPhone())
                .email(hotel.getEmail())
                .description(hotel.getDescription())
                .imageUrl(hotel.getImage_url())
                .status(hotel.getStatus())
                .companyId(hotel.getCompany().getId())
                .companyName(hotel.getCompany().getName())
                .amenities(hotel.getAmenities())
                .build();
    }
}
