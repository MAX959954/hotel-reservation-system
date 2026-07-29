package room;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import testsupport.MinimalTestApplication;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
@ContextConfiguration(classes = {MinimalTestApplication.class, RoomController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoomService roomService;

    private RoomResponse response;

    private RoomResponse sampleResponse() {
        return RoomResponse.builder()
                .id(1L)
                .number("101")
                .type(RoomType.DOUBLE)
                .pricePerNight(100.0)
                .capacity(2)
                .floor(1)
                .status(RoomStatus.AVAILABLE)
                .description("Nice room")
                .createdAt(LocalDateTime.now())
                .hotelId(1L)
                .hotelName("Grand Hotel")
                .build();
    }

    private RoomRequest validRequest() {
        RoomRequest request = new RoomRequest();
        request.setNumber("101");
        request.setType(RoomType.DOUBLE);
        request.setPricePerNight(100.0);
        request.setCapacity(2);
        request.setFloor(1);
        request.setDescription("Nice room");
        request.setHotelId(1L);
        return request;
    }

    @Test
    void create_returns201_whenRequestValid() throws Exception {
        given(roomService.create(any(RoomRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/rooms")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.number").value("101"));
    }

    @Test
    void create_returns400_whenRequestInvalid() throws Exception {
        RoomRequest invalid = validRequest();
        invalid.setNumber(null);
        invalid.setCapacity(0);

        mockMvc.perform(post("/api/rooms")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200_whenFound() throws Exception {
        given(roomService.getById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/rooms/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void getById_returns400_whenServiceThrows() throws Exception {
        given(roomService.getById(1L)).willThrow(new IllegalStateException("Room not found by that id: 1"));

        mockMvc.perform(get("/api/rooms/{id}", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Room not found by that id: 1"));
    }

    @Test
    void getByHotel_returns200() throws Exception {
        given(roomService.getByHotel(1L)).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/rooms/hotels/{hotelId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    void updateStatus_returns200() throws Exception {
        RoomResponse updated = sampleResponse();
        updated.setStatus(RoomStatus.MAINTENANCE);
        given(roomService.updateStatus(anyLong(), any(RoomStatus.class))).willReturn(updated);

        mockMvc.perform(patch("/api/rooms/{id}/status", 1L).param("status", "MAINTENANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void update_returns200_whenRequestValid() throws Exception {
        given(roomService.update(anyLong(), any(RoomRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(patch("/api/rooms/{id}", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(delete("/api/rooms/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(roomService).delete(1L);
    }

    @Test
    void delete_returns400_whenServiceThrows() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Room not found by that id: 1"))
                .when(roomService).delete(1L);

        mockMvc.perform(delete("/api/rooms/{id}", 1L))
                .andExpect(status().isBadRequest());
    }
}
