package user;

// Carries the plaintext code out of the transaction that generated it — only the hash is
// ever persisted, so the listener that actually emails the code has to receive it this way.
record OtpCodeGeneratedEvent(String identifier, String code) {
}
