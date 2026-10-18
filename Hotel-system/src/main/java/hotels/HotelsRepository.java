package hotels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HotelsRepository extends JpaRepository<Hotels, Long> {

    List<Hotels> findByCompanyId (Long companyId) ;

    List<Hotels> findByStatus(Hotel_Status status);

    List<Hotels> findByCity (String city) ;

    List<Hotels> findByCountry (String country) ;

    List<Hotels> findByPropertyType (PropertyType propertyType) ;

    // Filter by star rating
    @Query("SELECT h FROM Hotels h WHERE h.star_rating = :starRating")
    List<Hotels> findByStar_rating(@Param("starRating") Integer starRating);

    // Active hotels in a specific city
    List<Hotels> findByCityAndStatus(String city, Hotel_Status status);

    // Find by company and status
    List<Hotels> findByCompanyIdAndStatus(Long companyId, Hotel_Status status);

    Optional<Hotels> findByEmail(String email);

    // Public search only lists hotels a guest could actually deal with: not taken off the
    // market (INACTIVE/CLOSED/SUSPENDED) and run by an ACTIVE company. UNDER_RENOVATION
    // and COMINGS_SOON stay listed (the detail page labels them) but aren't bookable —
    // see BookingServiceImpl.create. The company's own panel uses findByCompanyId, which
    // deliberately still returns every hotel.
    String PUBLICLY_LISTED = " AND h.status NOT IN (hotels.Hotel_Status.INACTIVE, hotels.Hotel_Status.CLOSED, " +
            "hotels.Hotel_Status.SUSPENDED) AND h.company.status = companies.CompaniesStatus.ACTIVE";

    @Query("SELECT h FROM Hotels h WHERE h.city = :city" + PUBLICLY_LISTED)
    List<Hotels> findListedByCity(@Param("city") String city);

    @Query("SELECT h FROM Hotels h WHERE h.country = :country" + PUBLICLY_LISTED)
    List<Hotels> findListedByCountry(@Param("country") String country);

    @Query("SELECT h FROM Hotels h WHERE h.star_rating = :starRating" + PUBLICLY_LISTED)
    List<Hotels> findListedByStarRating(@Param("starRating") Integer starRating);

    @Query("SELECT h FROM Hotels h WHERE h.propertyType = :propertyType" + PUBLICLY_LISTED)
    List<Hotels> findListedByPropertyType(@Param("propertyType") PropertyType propertyType);
}
