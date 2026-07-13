package exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;

// it lets you centralize exception handling for all @RestControllers
// instead of wrapping every controller method in try/catch.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //tells Spring: when a ResourceNotFoundException bubbles up from any controller,
    // route it to this method instead of the default handling.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> hasNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND , ex.getMessage() , null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.BAD_REQUEST , ex.getMessage() , null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST , "Validation failed" , errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleContraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return  build(HttpStatus.BAD_REQUEST , "Validation failed" , errors);
    }

    // Requests to routes with no matching controller (Spring's DispatcherServlet falls
    // back to static-resource lookup and throws this when nothing matches either).
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND , "No such endpoint" , null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED , ex.getMessage() , null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR , "An unexpected error occured" , null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status , String message , List<String> errors){
        ApiError error = ApiError.builder()
                .status(status.value())
                .message(message)
                .timestamp(LocalDateTime.now())
                .errors(errors)
                .build();
        return ResponseEntity.status(status).body(error);
    }

}
