package hotels;

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

@WebMvcTest(HotelController.class)
@ContextConfiguration(classes = {MinimalTestApplication.class, HotelController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HotelService hotelService;

    private HotelResponse sampleResponse() {
        return HotelResponse.builder()
                .id(1L)
                .name("Grand Hotel")
                .city("Paris")
                .country("France")
                .startRating(4)
                .status(Hotel_Status.ACTIVE)
                .companyId(1L)
                .companyName("Acme Hospitality")
                .build();
    }

    private CreateHotelRequest validRequest() {
        CreateHotelRequest request = new CreateHotelRequest();
        request.setName("Grand Hotel");
        request.setCity("Paris");
        request.setCountry("France");
        request.setAddress("1 Rue de Rivoli");
        request.setRating(4);
        request.setPhone("+123456789");
        request.setEmail("contact@grandhotel.com");
        request.setDescription("A lovely hotel");
        request.setImageUrl("http://example.com/image.jpg");
        request.setCompanyId(1L);
        return request;
    }

    @Test
    void create_returns201_whenRequestValid() throws Exception {
        given(hotelService.create(any(CreateHotelRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/hotels")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void create_returns400_whenRatingOutOfRange() throws Exception {
        CreateHotelRequest invalid = validRequest();
        invalid.setRating(10);

        mockMvc.perform(post("/api/hotels")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_whenServiceThrows() throws Exception {
        given(hotelService.create(any(CreateHotelRequest.class)))
                .willThrow(new IllegalStateException("Company not found1"));

        mockMvc.perform(post("/api/hotels")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200_whenFound() throws Exception {
        given(hotelService.getById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/hotels/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Grand Hotel"));
    }

    @Test
    void getById_returns400_whenNotFound() throws Exception {
        given(hotelService.getById(1L)).willThrow(new IllegalStateException("Hotel not found 1"));

        mockMvc.perform(get("/api/hotels/{id}", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByCity_returns200() throws Exception {
        given(hotelService.getByCity("Paris")).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/hotels/city/{city}", "Paris"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("Paris"));
    }

    @Test
    void updateStatus_returns200() throws Exception {
        HotelResponse updated = sampleResponse();
        updated.setStatus(Hotel_Status.CLOSED);
        given(hotelService.updateStatus(anyLong(), any(Hotel_Status.class))).willReturn(updated);

        mockMvc.perform(patch("/api/hotels/{id}/status", 1L).param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(delete("/api/hotels/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(hotelService).delete(1L);
    }

    @Test
    void delete_returns400_whenServiceThrows() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Hotel not found 1"))
                .when(hotelService).delete(1L);

        mockMvc.perform(delete("/api/hotels/{id}", 1L))
                .andExpect(status().isBadRequest());
    }
}
