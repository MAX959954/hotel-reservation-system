package roomtype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomCategoryRepository extends JpaRepository<RoomCategory, Long> {

    Optional<RoomCategory> findByName(String name);

    @Query("SELECT rt FROM RoomCategory rt WHERE rt.base_price <= :maxPrice")
    List<RoomCategory> findByBasePriceLessThanEqual(@Param("maxPrice") Double maxPrice);

    @Query("SELECT rt FROM RoomCategory rt WHERE rt.max_occupansy >= :guestCount")
    List<RoomCategory> findByMaxOccupansyGreaterThanEqual(@Param("guestCount") Integer guestCount);

    boolean existsByName(String name);
}
