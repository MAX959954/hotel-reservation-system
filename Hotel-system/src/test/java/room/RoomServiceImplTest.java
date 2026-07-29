package room;

import hotels.Hotels;
import hotels.HotelsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class RoomServiceImplTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private HotelsRepository hotelsRepository;

    @InjectMocks
    private RoomServiceImpl roomService;

    private Hotels hotel;
    private Room room;
    private RoomRequest request;

    @BeforeEach
    void setUp() {
        hotel = Hotels.builder()
                .id(1L)
                .name("Grand Hotel")
                .build();

        room = Room.builder()
                .id(1L)
                .number("101")
                .type(RoomType.DOUBLE)
                .price_per_night(100.0)
                .capacity(2)
                .floor(1)
                .status(RoomStatus.AVAILABLE)
                .description("Nice room")
                .createdAt(LocalDateTime.now())
                .hotel(hotel)
                .build();

        request = new RoomRequest();
        request.setNumber("101");
        request.setType(RoomType.DOUBLE);
        request.setPricePerNight(100.0);
        request.setCapacity(2);
        request.setFloor(1);
        request.setDescription("Nice room");
        request.setHotelId(1L);
    }

    // ---------- create ----------

    @Test
    void create_savesRoomAndReturnsResponse_whenHotelExists() {
        given(hotelsRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomRepository.save(any(Room.class))).willReturn(room);

        RoomResponse response = roomService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNumber()).isEqualTo("101");
        assertThat(response.getType()).isEqualTo(RoomType.DOUBLE);
        assertThat(response.getPricePerNight()).isEqualTo(100.0);
        assertThat(response.getCapacity()).isEqualTo(2);
        assertThat(response.getFloor()).isEqualTo(1);
        assertThat(response.getHotelId()).isEqualTo(1L);
        assertThat(response.getHotelName()).isEqualTo("Grand Hotel");

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.getNumber()).isEqualTo("101");
        assertThat(saved.getHotel()).isEqualTo(hotel);
    }

    @Test
    void create_throws_whenHotelNotFound() {
        given(hotelsRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hotel not found by that id");

        verify(roomRepository, never()).save(any());
    }

    // ---------- getById ----------

    @Test
    void getById_returnsRoom_whenFound() {
        given(roomRepository.findById(1L)).willReturn(Optional.of(room));

        RoomResponse response = roomService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNumber()).isEqualTo("101");
    }

    @Test
    void getById_throws_whenNotFound() {
        given(roomRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getById(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Room not found by that id");
    }

    // ---------- getByHotel ----------

    @Test
    void getByHotel_returnsRooms() {
        given(roomRepository.findByHotelId(1L)).willReturn(List.of(room));

        List<RoomResponse> responses = roomService.getByHotel(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(1L);
    }

    // ---------- getByType ----------

    @Test
    void getByType_returnsRooms() {
        given(roomRepository.findByType(RoomType.DOUBLE)).willReturn(List.of(room));

        List<RoomResponse> responses = roomService.getByType(RoomType.DOUBLE);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getType()).isEqualTo(RoomType.DOUBLE);
    }

    // ---------- getByStatus ----------

    @Test
    void getByStatus_returnsRooms() {
        given(roomRepository.findByStatus(RoomStatus.AVAILABLE)).willReturn(List.of(room));

        List<RoomResponse> responses = roomService.getByStatus(RoomStatus.AVAILABLE);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    // ---------- getByHotelAndStatus ----------

    @Test
    void getByHotelAndStatus_returnsRooms() {
        given(roomRepository.findByHotelIdAndStatus(1L, RoomStatus.AVAILABLE)).willReturn(List.of(room));

        List<RoomResponse> responses = roomService.getByHotelAndStatus(1L, RoomStatus.AVAILABLE);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getHotelId()).isEqualTo(1L);
    }

    // ---------- getByHotelAndCapacity ----------

    @Test
    void getByHotelAndCapacity_returnsRooms() {
        given(roomRepository.findByHotelIdAndCapacityGreaterThanEqual(1L, 2)).willReturn(List.of(room));

        List<RoomResponse> responses = roomService.getByHotelAndCapacity(1L, 2);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCapacity()).isEqualTo(2);
    }

    // ---------- getAvailableRooms ----------

    @Test
    void getAvailableRooms_returnsRooms() {
        LocalDateTime checkIn = LocalDateTime.now();
        LocalDateTime checkOut = checkIn.plusDays(2);
        given(roomRepository.findAvailableRooms(1L, checkIn, checkOut)).willReturn(List.of(room));

        List<RoomResponse> responses = roomService.getAvailableRooms(1L, checkIn, checkOut);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(1L);
    }

    // ---------- updateStatus ----------

    @Test
    void updateStatus_updatesAndReturnsRoom_whenFound() {
        given(roomRepository.findById(1L)).willReturn(Optional.of(room));
        given(roomRepository.save(any(Room.class))).willReturn(room);

        RoomResponse response = roomService.updateStatus(1L, RoomStatus.MAINTENANCE);

        assertThat(response.getStatus()).isEqualTo(RoomStatus.MAINTENANCE);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RoomStatus.MAINTENANCE);
    }

    @Test
    void updateStatus_throws_whenRoomNotFound() {
        given(roomRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.updateStatus(1L, RoomStatus.MAINTENANCE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Room not found by that id");

        verify(roomRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void update_updatesAndReturnsRoom_whenValid() {
        RoomRequest updateRequest = new RoomRequest();
        updateRequest.setNumber("202");
        updateRequest.setType(RoomType.SUITE);
        updateRequest.setPricePerNight(250.0);
        updateRequest.setCapacity(4);
        updateRequest.setFloor(2);
        updateRequest.setDescription("Updated room");
        updateRequest.setHotelId(1L);

        given(roomRepository.findById(1L)).willReturn(Optional.of(room));
        given(hotelsRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomRepository.save(any(Room.class))).willReturn(room);

        RoomResponse response = roomService.update(1L, updateRequest);

        assertThat(response.getNumber()).isEqualTo("202");
        assertThat(response.getType()).isEqualTo(RoomType.SUITE);
        assertThat(response.getPricePerNight()).isEqualTo(250.0);
        assertThat(response.getCapacity()).isEqualTo(4);
        assertThat(response.getFloor()).isEqualTo(2);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("Updated room");
    }

    @Test
    void update_throws_whenRoomNotFound() {
        given(roomRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.update(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Room not found by that id");

        verify(roomRepository, never()).save(any());
    }

    @Test
    void update_throws_whenHotelNotFound() {
        given(roomRepository.findById(1L)).willReturn(Optional.of(room));
        given(hotelsRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.update(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hotel not found by that id");

        verify(roomRepository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    void delete_deletesRoom_whenFound() {
        given(roomRepository.findById(1L)).willReturn(Optional.of(room));

        roomService.delete(1L);

        verify(roomRepository).delete(room);
    }

    @Test
    void delete_throws_whenRoomNotFound() {
        given(roomRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Room not found by that id");
    }
}
