package room;

import companies.CompaniesResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")

public class RoomController {
    private final RoomService roomService;

    @PostMapping()
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getById(id));
    }

    @GetMapping("/hotels/{hotelId}")
    public ResponseEntity<List<RoomResponse>> getByHotel(@PathVariable Long hotelId) {
        return ResponseEntity.ok(roomService.getByHotel(hotelId));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<RoomResponse>> getByType(@PathVariable RoomType type) {
        return ResponseEntity.ok(roomService.getByType(type));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<RoomResponse>> getByStatus(@PathVariable RoomStatus status) {
        return ResponseEntity.ok(roomService.getByStatus(status));
    }

    @GetMapping("/hotels/{hotelId}/status/{status}")
    public ResponseEntity<List<RoomResponse>> getByHotelAndStatus(@PathVariable Long hotelId , @PathVariable  RoomStatus status) {
        return ResponseEntity.ok(roomService.getByHotelAndStatus(hotelId , status));
    }

    @GetMapping("/hotels/{hotelId}/capacity/{guestCount}")
    public ResponseEntity<List<RoomResponse>> getByHotelAndCapacity(@PathVariable Long hotelId ,@PathVariable Integer guestCount ) {
        return ResponseEntity.ok(roomService.getByHotelAndCapacity(hotelId , guestCount));
    }

    @GetMapping("/hotels/{hotelId}/available")
    public ResponseEntity<List<RoomResponse>> getAvailableRooms(@PathVariable Long hotelId ,
                                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkIn ,
                                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkOut ) {
        return ResponseEntity.ok(roomService.getAvailableRooms(hotelId , checkIn  , checkOut));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RoomResponse> updateStatus(@PathVariable Long id , @Valid @RequestParam RoomStatus status) {
        return ResponseEntity.ok(roomService.updateStatus(id , status));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RoomResponse> update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }


}
