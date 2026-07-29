package exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void hasNotFound_returns404_withMessage() {
        ResponseEntity<ApiError> response = handler.hasNotFound(new ResourceNotFoundException("Room not found: 1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Room not found: 1");
        assertThat(response.getBody().getTimestamp()).isNotNull();
        assertThat(response.getBody().getErrors()).isNull();
    }

    @Test
    void handleIllegalState_returns400_withMessage() {
        ResponseEntity<ApiError> response = handler.handleIllegalState(new IllegalStateException("Room not found by that id: 1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Room not found by that id: 1");
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<ApiError> response = handler.handleAccessDenied(new AccessDeniedException("Access is denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");
    }

    @Test
    void handleValidation_returns400_withFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        given(bindingResult.getFieldErrors()).willReturn(List.of(
                new FieldError("roomRequest", "number", "must not be blank"),
                new FieldError("roomRequest", "capacity", "must be greater than 0")
        ));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                mock(org.springframework.core.MethodParameter.class), bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getErrors()).containsExactlyInAnyOrder(
                "number: must not be blank",
                "capacity: must be greater than 0"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleContraintViolation_returns400_withViolations() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        given(path.toString()).willReturn("rating");
        given(violation.getPropertyPath()).willReturn(path);
        given(violation.getMessage()).willReturn("must be between 1 and 5");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiError> response = handler.handleContraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getErrors()).containsExactly("rating: must be between 1 and 5");
    }

    @Test
    void handleNoResourceFound_returns404() {
        ResponseEntity<ApiError> response = handler.handleNoResourceFound(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/api/unknown"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("No such endpoint");
    }

    @Test
    void handleMethodNotSupported_returns405() {
        ResponseEntity<ApiError> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void handleGeneric_returns500_withGenericMessage() {
        ResponseEntity<ApiError> response = handler.handleGeneric(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occured");
    }
}
