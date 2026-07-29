package reviews;

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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewsController.class)
@ContextConfiguration(classes = {MinimalTestApplication.class, ReviewsController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class ReviewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewsService reviewsService;

    private ReviewsResponse sampleResponse() {
        return ReviewsResponse.builder()
                .id(1L)
                .bookingId(1L)
                .userId(1L)
                .userFullName("Jane Doe")
                .roomId(1L)
                .roomNumber("101")
                .hotelName("Grand Hotel")
                .rating(5)
                .comment("Great stay")
                .approved(false)
                .build();
    }

    private ReviewsRequest validRequest() {
        ReviewsRequest request = new ReviewsRequest();
        request.setBookingId(1L);
        request.setRating(5);
        request.setComment("Great stay");
        return request;
    }

    @Test
    void create_returns201_whenRequestValid() throws Exception {
        given(reviewsService.create(any(ReviewsRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/reviews")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void create_returns400_whenRatingOutOfRange() throws Exception {
        ReviewsRequest invalid = validRequest();
        invalid.setRating(6);

        mockMvc.perform(post("/api/reviews")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_whenServiceThrows() throws Exception {
        given(reviewsService.create(any(ReviewsRequest.class)))
                .willThrow(new IllegalStateException("A review already exists for that booking"));

        mockMvc.perform(post("/api/reviews")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200_whenFound() throws Exception {
        given(reviewsService.getById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/reviews/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void getById_returns400_whenNotFound() throws Exception {
        given(reviewsService.getById(1L)).willThrow(new IllegalStateException("Review not found by id: 1"));

        mockMvc.perform(get("/api/reviews/{id}", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByRoom_returns200() throws Exception {
        given(reviewsService.getByRoom(1L)).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/reviews/room/{roomId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomId").value(1L));
    }

    @Test
    void getAverageRating_returns200() throws Exception {
        given(reviewsService.getAverageRatingByRoom(1L)).willReturn(4.5);

        mockMvc.perform(get("/api/reviews/room/{roomId}/rating", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string("4.5"));
    }

    @Test
    void approve_returns200() throws Exception {
        ReviewsResponse approved = sampleResponse();
        approved.setApproved(true);
        given(reviewsService.approve(anyLong())).willReturn(approved);

        mockMvc.perform(patch("/api/reviews/{id}/approve", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void approve_returns400_whenAlreadyApproved() throws Exception {
        given(reviewsService.approve(1L)).willThrow(new IllegalStateException("Review is already approved"));

        mockMvc.perform(patch("/api/reviews/{id}/approve", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(delete("/api/reviews/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(reviewsService).delete(1L);
    }

    @Test
    void delete_returns400_whenServiceThrows() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Review not found by id: 1"))
                .when(reviewsService).delete(1L);

        mockMvc.perform(delete("/api/reviews/{id}", 1L))
                .andExpect(status().isBadRequest());
    }
}
