package booking;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@ContextConfiguration(classes = {MinimalTestApplication.class, BookingController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false, printOnlyOnFailure = true)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    private BookingResponse sampleResponse() {
        return BookingResponse.builder()
                .Id(1L)
                .userId(1L)
                .userFullName("Jane Doe")
                .roomId(1L)
                .roomNumber("101")
                .hotelId(1L)
                .hotelName("Grand Hotel")
                .checkIn(LocalDateTime.now().plusDays(1))
                .checkOut(LocalDateTime.now().plusDays(3))
                .guestCount(2)
                .bookingStatus(BookingStatus.PENDING)
                .totalPrice(200.0)
                .build();
    }

    private BookingRequest validRequest() {
        BookingRequest request = new BookingRequest();
        request.setRoomId(1L);
        request.setCheckIn(LocalDateTime.now().plusDays(1));
        request.setCheckOut(LocalDateTime.now().plusDays(3));
        request.setGuestCount(2);
        return request;
    }

    @Test
    void create_returns201_whenRequestValid() throws Exception {
        given(bookingService.create(any(BookingRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/bookings")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(1L));
    }

    @Test
    void create_returns400_whenCheckInInPast() throws Exception {
        BookingRequest invalid = validRequest();
        invalid.setCheckIn(LocalDateTime.now().minusDays(1));

        mockMvc.perform(post("/api/bookings")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_whenServiceThrows() throws Exception {
        given(bookingService.create(any(BookingRequest.class)))
                .willThrow(new IllegalStateException("Room is already booked for the selected dates"));

        mockMvc.perform(post("/api/bookings")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirm_returns200() throws Exception {
        BookingResponse confirmed = sampleResponse();
        confirmed.setBookingStatus(BookingStatus.CONFIRMED);
        given(bookingService.confirm(anyLong())).willReturn(confirmed);

        mockMvc.perform(patch("/api/bookings/{id}/confirm", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));
    }

    @Test
    void cancel_returns200() throws Exception {
        BookingResponse cancelled = sampleResponse();
        cancelled.setBookingStatus(BookingStatus.CANCELLED);
        given(bookingService.cancel(anyLong())).willReturn(cancelled);

        mockMvc.perform(patch("/api/bookings/{id}/cancel", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(delete("/api/bookings/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(bookingService).delete(1L);
    }

    @Test
    void delete_returns400_whenServiceThrows() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Booking not found by id: 1"))
                .when(bookingService).delete(1L);

        mockMvc.perform(delete("/api/bookings/{id}", 1L))
                .andExpect(status().isBadRequest());
    }
}
