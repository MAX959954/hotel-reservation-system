package com.folio.hotels;

import com.folio.booking.BookingService;
import com.folio.exception.ResourceNotFoundException;
import com.folio.companies.Company;
import com.folio.companies.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService{

    private static final String BASE_LOCALE = "en";

    private final HotelRepository hotelsRepository;
    private final CompanyRepository companiesRepository;
    private final HotelTranslationRepository hotelTranslationRepository;
    private final BookingService bookingService;

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"hotelsByCity", "hotelsByCountry", "hotelsByCompany", "hotelsByRating", "hotelsByType"}, allEntries = true)
    public  HotelResponse create(CreateHotelRequest request) {
        Company company = companiesRepository.findById(request.getCompanyId()).orElseThrow(() -> new ResourceNotFoundException("Company not found "+ request.getCompanyId()));

        Hotel hotel = Hotel.builder()
                .name(request.getName())
                .city(request.getCity())
                .country(request.getCountry())
                .address(request.getAddress())
                .starRating(request.getRating())
                .phone(request.getPhone())
                .email(request.getEmail())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .company(company)
                .propertyType(request.getPropertyType() != null ? request.getPropertyType() : PropertyType.HOTEL)
                .amenities(request.getAmenities() != null ? request.getAmenities() : new HashSet<>())
                .build();

        return toResponse(hotelsRepository.save(hotel), BASE_LOCALE);
    }

    @Override
    @Cacheable(cacheNames = "hotelById", key = "#id + '-' + #locale")
    public HotelResponse getById(Long id, String locale) {
        return toResponse(findById(id), locale);
    }

    @Override
    @Cacheable(cacheNames = "hotelsByCity", key = "#city + '-' + #locale")
    public List<HotelResponse> getByCity(String city, String locale) {
        return hotelsRepository.findListedByCity(city).stream().map(h -> toResponse(h, locale)).toList();
    }

    @Override
    @Cacheable(cacheNames = "hotelsByCountry", key = "#country + '-' + #locale")
    public List<HotelResponse> getByCountry(String country, String locale) {
        return hotelsRepository.findListedByCountry(country).stream().map(h -> toResponse(h, locale)).toList();
    }

    @Override
    @Cacheable(cacheNames = "hotelsByCompany", key = "#companyId + '-' + #locale")
    public List<HotelResponse> getByCompany(Long companyId, String locale) {
        return hotelsRepository.findByCompanyId(companyId).stream().map(h -> toResponse(h, locale)).toList();
    }

    @Override
    @Cacheable(cacheNames = "hotelsByRating", key = "#rating + '-' + #locale")
    public List<HotelResponse> getByRating(Integer rating, String locale) {
        return hotelsRepository.findListedByStarRating(rating).stream().map(h -> toResponse(h, locale)).toList();
    }

    @Override
    @Cacheable(cacheNames = "hotelsByType", key = "#propertyType + '-' + #locale")
    public List<HotelResponse> getByPropertyType(PropertyType propertyType, String locale) {
        return hotelsRepository.findListedByPropertyType(propertyType).stream().map(h -> toResponse(h, locale)).toList();
    }

    @Override
    @Transactional
    // Both hotelById and the list caches now key on "id-locale" / "param-locale" — a
    // targeted evict by #id alone can no longer match, since it doesn't know which
    // locale(s) are cached. allEntries clears every locale's copy, which is the
    // correct (if slightly broader) fix.
    @Caching(evict = {
            @CacheEvict(cacheNames = "hotelById", allEntries = true),
            @CacheEvict(cacheNames = {"hotelsByCity", "hotelsByCountry", "hotelsByCompany", "hotelsByRating", "hotelsByType"}, allEntries = true),
            // availability only lists rooms of ACTIVE hotels (RoomRepository.findAvailableRooms)
            @CacheEvict(cacheNames = "roomsAvailability", allEntries = true)
    })
    public HotelResponse updateStatus(Long id , HotelStatus status) {
        Hotel hotel =  findById(id);
        hotel.setStatus(status);
        Hotel saved = hotelsRepository.save(hotel);
        // A hotel that shuts down can't host its upcoming guests: cancel those bookings
        // with a full refund (same transaction — a failed refund rejects the change).
        // UNDER_RENOVATION / COMINGS_SOON only stop new bookings; existing ones stay with
        // the hotel's staff, who keep their access.
        if (SHUT_DOWN_STATUSES.contains(status)) {
            bookingService.cancelUpcomingForHotel(id);
        }
        return toResponse(saved, BASE_LOCALE);
    }

    private static final Set<HotelStatus> SHUT_DOWN_STATUSES =
            EnumSet.of(HotelStatus.CLOSED, HotelStatus.SUSPENDED, HotelStatus.INACTIVE);

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "hotelById", allEntries = true),
            @CacheEvict(cacheNames = {"hotelsByCity", "hotelsByCountry", "hotelsByCompany", "hotelsByRating", "hotelsByType"}, allEntries = true)
    })
    public HotelResponse update(Long id, CreateHotelRequest request) {
        Hotel hotel = findById(id);
        hotel.setName(request.getName());
        hotel.setCity(request.getCity());
        hotel.setCountry(request.getCountry());
        hotel.setAddress(request.getAddress());
        hotel.setStarRating(request.getRating());
        hotel.setPhone(request.getPhone());
        hotel.setEmail(request.getEmail());
        hotel.setDescription(request.getDescription());
        hotel.setImageUrl(request.getImageUrl());
        if (request.getPropertyType() != null) hotel.setPropertyType(request.getPropertyType());
        hotel.setAmenities(request.getAmenities() != null ? request.getAmenities() : new HashSet<>());
        // company and status are deliberately untouched here — moving a hotel between
        // companies or flipping its status isn't what a details-edit form is for.
        return toResponse(hotelsRepository.save(hotel), BASE_LOCALE);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "hotelById", allEntries = true),
            @CacheEvict(cacheNames = {"hotelsByCity", "hotelsByCountry", "hotelsByCompany", "hotelsByRating", "hotelsByType"}, allEntries = true)
    })
    public void delete(Long id) {
        hotelsRepository.delete(findById(id));
    }

    private Hotel findById(Long id) {
        return hotelsRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel not found " + id));
    }

    /** Falls back to the base (English) description whenever the requested locale is
     *  English itself, blank, or simply has no translation row yet for this hotel. */
    private String resolveDescription(Hotel hotel, String locale) {
        if (locale == null || locale.isBlank() || locale.equalsIgnoreCase(BASE_LOCALE)) {
            return hotel.getDescription();
        }
        return hotelTranslationRepository.findByHotelIdAndLocale(hotel.getId(), locale.toLowerCase())
                .map(HotelTranslation::getDescription)
                .orElse(hotel.getDescription());
    }

    private HotelResponse toResponse(Hotel hotel, String locale) {
        return HotelResponse.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .city(hotel.getCity())
                .country(hotel.getCountry())
                .address(hotel.getAddress())
                .startRating(hotel.getStarRating())
                .phone(hotel.getPhone())
                .email(hotel.getEmail())
                .description(resolveDescription(hotel, locale))
                .imageUrl(hotel.getImageUrl())
                .status(hotel.getStatus())
                .propertyType(hotel.getPropertyType())
                .companyId(hotel.getCompany().getId())
                .companyName(hotel.getCompany().getName())
                // Copied into a plain set on purpose. hotel.getAmenities() is a Hibernate
                // PersistentSet, and handing that straight to the DTO leaks a persistence
                // type across the layer boundary: the Redis serializer records the concrete
                // runtime class as the type id, writes
                // "amenities":["org.hibernate.collection.spi.PersistentSet",[...]], and then
                // cannot read its own entry back — every cached lookup 500s on the second call.
                .amenities(hotel.getAmenities() == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(hotel.getAmenities()))
                .build();
    }
}
