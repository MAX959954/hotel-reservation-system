package hotels;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HotelTranslationRepository extends JpaRepository<HotelTranslation, Long> {
    Optional<HotelTranslation> findByHotelIdAndLocale(Long hotelId, String locale);
}
