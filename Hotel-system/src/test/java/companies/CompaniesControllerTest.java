package companies;

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

@WebMvcTest(CompaniesController.class)
@ContextConfiguration(classes = {MinimalTestApplication.class, CompaniesController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class CompaniesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompaniesService companiesService;

    private CompaniesResponse sampleResponse() {
        return CompaniesResponse.builder()
                .id(1L)
                .name("Acme Hospitality")
                .legalName("Acme Hospitality LLC")
                .email("contact@acme.com")
                .phone("+123456789")
                .city("Paris")
                .country("France")
                .status(CompaniesStatus.PENDING_VERIFICATION)
                .build();
    }

    private CompaniesRequest validRequest() {
        CompaniesRequest request = new CompaniesRequest();
        request.setName("Acme Hospitality");
        request.setLegalName("Acme Hospitality LLC");
        request.setEmail("contact@acme.com");
        request.setPhone("+123456789");
        request.setAddress("1 Main St");
        request.setCity("Paris");
        request.setCountry("France");
        request.setWebSite("https://acme.com");
        return request;
    }

    @Test
    void create_returns201_whenRequestValid() throws Exception {
        given(companiesService.create(any(CompaniesRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/companies")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void create_returns400_whenEmailInvalid() throws Exception {
        CompaniesRequest invalid = validRequest();
        invalid.setEmail("not-an-email");

        mockMvc.perform(post("/api/companies")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_whenServiceThrows() throws Exception {
        given(companiesService.create(any(CompaniesRequest.class)))
                .willThrow(new IllegalStateException("Company with that email already exists: contact@acme.com"));

        mockMvc.perform(post("/api/companies")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Company with that email already exists: contact@acme.com"));
    }

    @Test
    void getById_returns200_whenFound() throws Exception {
        given(companiesService.getById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/companies/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Hospitality"));
    }

    @Test
    void getById_returns400_whenNotFound() throws Exception {
        given(companiesService.getById(1L)).willThrow(new IllegalStateException("Company not found: 1"));

        mockMvc.perform(get("/api/companies/{id}", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByCity_returns200() throws Exception {
        given(companiesService.getByCity("Paris")).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/companies/city/{city}", "Paris"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("Paris"));
    }

    @Test
    void updateStatus_returns200() throws Exception {
        CompaniesResponse updated = sampleResponse();
        updated.setStatus(CompaniesStatus.ACTIVE);
        given(companiesService.updateStatus(anyLong(), any(CompaniesStatus.class))).willReturn(updated);

        mockMvc.perform(patch("/api/companies/{id}/status", 1L).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void update_returns200_whenRequestValid() throws Exception {
        given(companiesService.update(anyLong(), any(CompaniesRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(put("/api/companies/{id}", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(delete("/api/companies/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(companiesService).delete(1L);
    }

    @Test
    void delete_returns400_whenServiceThrows() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Company not found: 1"))
                .when(companiesService).delete(1L);

        mockMvc.perform(delete("/api/companies/{id}", 1L))
                .andExpect(status().isBadRequest());
    }
}
