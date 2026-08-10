package exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Without this, Spring Security's default entry point for a pure-API config (no
 * httpBasic/formLogin configured) is Http403ForbiddenEntryPoint — a missing, expired, or
 * otherwise invalid JWT comes back as a bare 403 with no body, indistinguishable from a
 * real @PreAuthorize denial. The frontend's session-expiry handling only listens for 401
 * (see http.ts's response interceptor), so that 403 silently left the user "logged in"
 * with a dead token: the UI kept showing them as signed in while every real request kept
 * failing, with no prompt to sign back in.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError error = ApiError.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Your session has expired. Please sign in again.")
                .timestamp(LocalDateTime.now())
                .errors(null)
                .build();

        objectMapper.writeValue(response.getWriter(), error);
    }
}
