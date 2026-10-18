package exception;

// The request is valid but clashes with the current state of the data (e.g. deleting a
// booking that payments or reviews still refer to) — mapped to 409 by GlobalExceptionHandler.
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
