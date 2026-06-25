package room_type;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface Room_typeRepository extends JpaRepository<Room_type, Long> {

    Optional<Room_type> findByName(String name);

    List<Room_type> findByBasePriceLessThanEqual(Double maxPrice);

    List<Room_type> findByMaxOccupansyGreaterThanEqual(Integer guestCount);

    boolean existsByName(String name);
}
