package com.folio.room;

import com.folio.exception.ResourceNotFoundException;
import com.folio.hotels.Hotel;
import com.folio.hotels.HotelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService{

    private final RoomRepository roomRepository;
    private final HotelRepository hotelsRepository;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "roomsAvailability", allEntries = true)
    public RoomResponse create (RoomRequest request) {
        Hotel hotel = findHotelById(request.getHotelId());
        Room room = Room.builder()
                .number(request.getNumber())
                .type(request.getType())
                .pricePerNight(request.getPricePerNight())
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
    @Cacheable(cacheNames = "roomsAvailability", key = "#hotelId + '-' + #checkIn + '-' + #checkOut + '-' + #guestCount")
    public List<RoomResponse> getAvailableRooms(Long hotelId , LocalDateTime checkIn , LocalDateTime checkOut , Integer guestCount){
        return roomRepository.findAvailableRooms(hotelId , checkIn , checkOut , guestCount).stream().map(this :: toResponse).toList();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "roomsAvailability", allEntries = true)
    public RoomResponse updateStatus(Long id , RoomStatus status){
        Room room  = findById(id);
        room.setStatus(status);
        return toResponse(roomRepository.save(room));
    }


    @Override
    @Transactional
    @CacheEvict(cacheNames = "roomsAvailability", allEntries = true)
    public RoomResponse update(Long id , RoomRequest request) {
        Room room = findById(id);
        // Access is checked against the room's current hotel only (RoomController), so
        // accepting a different hotelId here would let staff of one company move a room
        // into another company's hotel. Same rule as HotelServiceImpl.update: an edit
        // form never moves an entity to another owner.
        if (!room.getHotel().getId().equals(request.getHotelId())) {
            throw new IllegalStateException("A room cannot be moved to another hotel");
        }
        room.setNumber(request.getNumber());
        room.setType(request.getType());
        room.setPricePerNight(request.getPricePerNight());
        room.setCapacity(request.getCapacity());
        room.setFloor(request.getFloor());
        room.setDescription(request.getDescription());
        return toResponse(roomRepository.save(room));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "roomsAvailability", allEntries = true)
    public void delete(Long id) {
        roomRepository.delete(findById(id));
    }

    private Room findById(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room not found by that id: " + id));
    }

    private Hotel findHotelById(Long id){
        return hotelsRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel not found by that id " + id));
    }

    private RoomResponse toResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .number(room.getNumber())
                .type(room.getType())
                .pricePerNight(room.getPricePerNight())
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
