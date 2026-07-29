package companyuser;

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

@WebMvcTest(CompanyUserController.class)
@ContextConfiguration(classes = {MinimalTestApplication.class, CompanyUserController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class CompanyUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompanyUserService companyUserService;

    private CompanyUserResponse sampleResponse() {
        return CompanyUserResponse.builder()
                .id(1L)
                .userId(1L)
                .userEmail("jane@example.com")
                .companyId(1L)
                .companyName("Acme Hospitality")
                .companyRole(CompanyRole.STAFF)
                .status(CompanyUserStatus.INVITED)
                .build();
    }

    private CompanyUserRequest validRequest() {
        CompanyUserRequest request = new CompanyUserRequest();
        request.setUserId(1L);
        request.setCompanyId(1L);
        request.setCompanyRole(CompanyRole.STAFF);
        return request;
    }

    @Test
    void invite_returns201_whenRequestValid() throws Exception {
        given(companyUserService.invite(any(CompanyUserRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/company_users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void invite_returns400_whenCompanyIdMissing() throws Exception {
        CompanyUserRequest invalid = validRequest();
        invalid.setCompanyId(null);

        mockMvc.perform(post("/company_users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invite_returns400_whenServiceThrows() throws Exception {
        given(companyUserService.invite(any(CompanyUserRequest.class)))
                .willThrow(new IllegalStateException("User is already is member of that company"));

        mockMvc.perform(post("/company_users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptInvite_returns200() throws Exception {
        CompanyUserResponse accepted = sampleResponse();
        accepted.setStatus(CompanyUserStatus.ACTIVE);
        given(companyUserService.acceptInvite(anyLong())).willReturn(accepted);

        mockMvc.perform(patch("/company_users/{id}/accept", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void acceptInvite_returns400_whenNotInvited() throws Exception {
        given(companyUserService.acceptInvite(1L))
                .willThrow(new IllegalStateException("Only invited members can accept invite"));

        mockMvc.perform(patch("/company_users/{id}/accept", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeRole_returns200() throws Exception {
        CompanyUserResponse updated = sampleResponse();
        updated.setCompanyRole(CompanyRole.MANAGER);
        given(companyUserService.changeRole(anyLong(), any(CompanyRole.class))).willReturn(updated);

        mockMvc.perform(post("/company_users/{id}/role", 1L).param("role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyRole").value("MANAGER"));
    }

    @Test
    void remove_returns204() throws Exception {
        mockMvc.perform(delete("/company_users/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(companyUserService).remove(1L);
    }

    @Test
    void remove_returns400_whenServiceThrows() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Company member not found by that id 1"))
                .when(companyUserService).remove(1L);

        mockMvc.perform(delete("/company_users/{id}", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByCompany_returns200() throws Exception {
        given(companyUserService.getByCompany(1L)).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/company_users/company/{companyId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyId").value(1L));
    }
}
