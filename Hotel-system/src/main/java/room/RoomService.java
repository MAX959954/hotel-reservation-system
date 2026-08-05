package room;

import java.time.LocalDateTime;
import java.util.List;

public interface RoomService {

    RoomResponse create(RoomRequest request);

    RoomResponse getById(Long id);

    List<RoomResponse> getByHotel(Long hotelId);

    List<RoomResponse> getByType(RoomType type);

    List<RoomResponse> getByStatus(RoomStatus status);

    List<RoomResponse> getByHotelAndStatus(Long hoytelId , RoomStatus status);

    List<RoomResponse> getByHotelAndCapacity(Long hotelId , Integer guestCount);

    List<RoomResponse> getAvailableRooms(Long hotelId , LocalDateTime checkIn , LocalDateTime checkOut , Integer guestCount);

    RoomResponse updateStatus(Long id , RoomStatus status);

    RoomResponse update(Long id , RoomRequest request);

    void delete(Long id);
}
