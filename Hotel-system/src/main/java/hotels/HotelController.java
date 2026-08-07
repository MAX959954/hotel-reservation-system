package hotels;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.hasRole(#request.companyId , 'OWNER' , 'MANAGER')")
    public ResponseEntity<HotelResponse> create(@Valid @RequestBody CreateHotelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotelService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HotelResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getById(id));
    }

    @GetMapping("/city/{city}")
    public ResponseEntity<List<HotelResponse>> getByCity(@PathVariable String city) {
        return ResponseEntity.ok(hotelService.getByCity(city));
    }

    @GetMapping("/country/{country}")
    public ResponseEntity<List<HotelResponse>> getByCountry(@PathVariable String country) {
        return ResponseEntity.ok(hotelService.getByCountry(country));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<HotelResponse>> getByCompanyId(@PathVariable Long companyId) {
        return ResponseEntity.ok(hotelService.getByCompany(companyId));
    }

    @GetMapping("/rating/{rating}")
    public ResponseEntity<List<HotelResponse>> getByRating(@PathVariable Integer rating) {
        return ResponseEntity.ok(hotelService.getByRating(rating));
    }

    @GetMapping("/type/{propertyType}")
    public ResponseEntity<List<HotelResponse>> getByPropertyType(@PathVariable PropertyType propertyType) {
        return ResponseEntity.ok(hotelService.getByPropertyType(propertyType));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.hasRoleForHotel(#id , 'OWNER' , 'MANAGER')")
    public ResponseEntity<HotelResponse> updateStatus(@PathVariable Long id, @RequestParam Hotel_Status status) {
        return ResponseEntity.ok(hotelService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.hasRoleForHotel(#id , 'OWNER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        hotelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
