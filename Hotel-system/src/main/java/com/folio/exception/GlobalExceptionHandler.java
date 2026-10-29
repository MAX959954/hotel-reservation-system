package com.folio.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT , ex.getMessage() , null);
    }

    // Safety net for any other write that breaks a database constraint (foreign key,
    // unique) — it's a conflict with existing data, not a server fault, so 409, not 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT , "The operation conflicts with existing data" , null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.BAD_REQUEST , ex.getMessage() , null);
    }

    // Without this, an AccessDeniedException thrown by a denied @PreAuthorize check
    // falls through to the generic Exception handler below and comes back as a 500
    // instead of the 403 Spring Security would otherwise produce.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN , "Access denied" , null);
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

    // Thrown by Spring itself before the controller runs, when a multipart upload exceeds
    // spring.servlet.multipart.max-file-size (10 MB, shared by every upload) — without this
    // it falls through to the generic 500 handler below. The tighter per-type limits
    // (avatars 5 MB) are checked later by the storage services with their own message.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.BAD_REQUEST, "File must be 10 MB or smaller.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED , ex.getMessage() , null);
    }

    // The client errors below are raised by Spring MVC before the controller runs. Without
    // these handlers they all reached handleGeneric and came back as 500, e.g. a
    // non-numeric id in /api/hotels/{id} or a malformed JSON body.

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST , "Invalid value for parameter '" + ex.getName() + "'" , null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST , "Missing parameter '" + ex.getParameterName() + "'" , null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex) {
        return build(HttpStatus.BAD_REQUEST , "Missing file '" + ex.getRequestPartName() + "'" , null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST , "Malformed request body" , null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE , "Unsupported content type" , null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR , "An unexpected error occurred" , null);
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
