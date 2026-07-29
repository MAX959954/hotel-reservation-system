package user;

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

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@ContextConfiguration(classes = {MinimalTestApplication.class, UserController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private AuthResponse sampleResponse() {
        return AuthResponse.builder()
                .token("token_abc")
                .email("jane@example.com")
                .roles(Set.of(Roles.GUEST))
                .build();
    }

    private RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane@example.com");
        request.setPassword("password123");
        request.setPhone("+123456789");
        return request;
    }

    private LogInRequest validLogInRequest() {
        LogInRequest request = new LogInRequest();
        request.setEmail("jane@example.com");
        request.setPassword("password123");
        return request;
    }

    @Test
    void register_returns201_whenRequestValid() throws Exception {
        given(userService.register(any(RegisterRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("token_abc"));
    }

    @Test
    void register_returns400_whenPasswordTooShort() throws Exception {
        RegisterRequest invalid = validRegisterRequest();
        invalid.setPassword("short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400_whenEmailAlreadyInUse() throws Exception {
        given(userService.register(any(RegisterRequest.class)))
                .willThrow(new IllegalStateException("Email already in use : jane@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logIn_returns200_whenCredentialsValid() throws Exception {
        given(userService.logIn(any(LogInRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validLogInRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void logIn_returns400_whenPasswordInvalid() throws Exception {
        given(userService.logIn(any(LogInRequest.class))).willThrow(new IllegalStateException("Invalid password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validLogInRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logIn_returns400_whenEmailBlank() throws Exception {
        LogInRequest invalid = validLogInRequest();
        invalid.setEmail("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }
}
