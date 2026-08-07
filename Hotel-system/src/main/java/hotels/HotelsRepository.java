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
}
