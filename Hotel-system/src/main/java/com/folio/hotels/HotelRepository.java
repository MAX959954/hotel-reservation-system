package com.folio.hotels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HotelRepository extends JpaRepository<Hotel, Long> {

    List<Hotel> findByCompanyId (Long companyId) ;

    List<Hotel> findByStatus(HotelStatus status);

    List<Hotel> findByCity (String city) ;

    List<Hotel> findByCountry (String country) ;

    List<Hotel> findByPropertyType (PropertyType propertyType) ;

    // Filter by star rating
    @Query("SELECT h FROM Hotel h WHERE h.starRating = :starRating")
    List<Hotel> findByStarRating(@Param("starRating") Integer starRating);

    // Active hotels in a specific city
    List<Hotel> findByCityAndStatus(String city, HotelStatus status);

    // Find by company and status
    List<Hotel> findByCompanyIdAndStatus(Long companyId, HotelStatus status);

    Optional<Hotel> findByEmail(String email);

    // Public search only lists hotels a guest could actually deal with: not taken off the
    // market (INACTIVE/CLOSED/SUSPENDED) and run by an ACTIVE company. UNDER_RENOVATION
    // and COMINGS_SOON stay listed (the detail page labels them) but aren't bookable —
    // see BookingServiceImpl.create. The company's own panel uses findByCompanyId, which
    // deliberately still returns every hotel.
    String PUBLICLY_LISTED = " AND h.status NOT IN (com.folio.hotels.HotelStatus.INACTIVE, com.folio.hotels.HotelStatus.CLOSED, " +
            "com.folio.hotels.HotelStatus.SUSPENDED) AND h.company.status = com.folio.companies.CompanyStatus.ACTIVE";

    @Query("SELECT h FROM Hotel h WHERE h.city = :city" + PUBLICLY_LISTED)
    List<Hotel> findListedByCity(@Param("city") String city);

    @Query("SELECT h FROM Hotel h WHERE h.country = :country" + PUBLICLY_LISTED)
    List<Hotel> findListedByCountry(@Param("country") String country);

    @Query("SELECT h FROM Hotel h WHERE h.starRating = :starRating" + PUBLICLY_LISTED)
    List<Hotel> findListedByStarRating(@Param("starRating") Integer starRating);

    @Query("SELECT h FROM Hotel h WHERE h.propertyType = :propertyType" + PUBLICLY_LISTED)
    List<Hotel> findListedByPropertyType(@Param("propertyType") PropertyType propertyType);
}
