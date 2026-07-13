package room;

import hotels.Hotels;
import hotels.HotelsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService{

    private final RoomRepository roomRepository;
    private final HotelsRepository hotelsRepository;

    @Override
    @Transactional
    public RoomResponse create (RoomRequest request) {
        Hotels hotel = findHotelById(request.getHotelId());
        Room room = Room.builder()
                .number(request.getNumber())
                .type(request.getType())
                .price_per_night(request.getPricePerNight())
                .capacity(request.getCapacity())
                .floor(request.getFloor())
                .description(request.getDescription())
                .hotel(hotel)
                .build();
        return toResponse(roomRepository.save(room));
    }

    @Override
    public RoomResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public List<RoomResponse> getByHotel(Long hotelId) {
        return roomRepository.findByHotelId(hotelId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<RoomResponse> getByType(RoomType type) {
        return roomRepository.findByType(type).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<RoomResponse> getByStatus(RoomStatus status) {
        return roomRepository.findByStatus(status).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<RoomResponse> getByHotelAndStatus(Long hotelId , RoomStatus status) {
        return roomRepository.findByHotelIdAndStatus(hotelId , status).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<RoomResponse> getByHotelAndCapacity(Long hotelId , Integer guestCount) {
        return roomRepository.findByHotelIdAndCapacityGreaterThanEqual(hotelId , guestCount).stream().map(this :: toResponse).toList();
    }


    @Override
    public List<RoomResponse> getAvailableRooms(Long hotelId , LocalDateTime checkIn , LocalDateTime checkOut){
        return roomRepository.findAvailableRooms(hotelId , checkIn , checkOut).stream().map(this :: toResponse).toList();
    }

    @Override
    @Transactional
    public RoomResponse updateStatus(Long id , RoomStatus status){
        Room room  = findById(id);
        room.setStatus(status);
        return toResponse(roomRepository.save(room));
    }


    @Override
    @Transactional
    public RoomResponse update(Long id , RoomRequest request) {
        Room room = findById(id);
        Hotels hotel = findHotelById(request.getHotelId());
        room.setNumber(request.getNumber());
        room.setType(request.getType());
        room.setPrice_per_night(request.getPricePerNight());
        room.setCapacity(request.getCapacity());
        room.setFloor(request.getFloor());
        room.setDescription(request.getDescription());
        room.setHotel(hotel);
        return toResponse(roomRepository.save(room));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        roomRepository.delete(findById(id));
    }

    private Room findById(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> new IllegalStateException("Room not found by that id: " + id));
    }

    private Hotels findHotelById(Long id){
        return hotelsRepository.findById(id).orElseThrow(() -> new IllegalStateException("Hotel not found by that id " + id));
    }

    private RoomResponse toResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .number(room.getNumber())
                .type(room.getType())
                .pricePerNight(room.getPrice_per_night())
                .capacity(room.getCapacity())
                .floor(room.getFloor())
                .status(room.getStatus())
                .description(room.getDescription())
                .createdAt(room.getCreatedAt())
                .hotelId(room.getHotel().getId())
                .hotelName(room.getHotel().getName())
                .build();
    }


}
