package payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // Without this, any authenticated user could pay against — or read — a booking that
    // isn't theirs, just by guessing an id. The booking's own guest is allowed through
    // isBookingOwner; staff is allowed through the same company-scoped check used
    // everywhere else in this controller.
    @PostMapping
    @PreAuthorize("@companyAuth.isBookingOwner(#request.bookingId) or hasAnyRole('ADMIN' , 'RECEPTIONIST') or @companyAuth.hasRoleForBooking(#request.bookingId , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<PaymentResponse> pay(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.pay(request));
    }

    // Idempotency-Key is optional and caller-supplied (the frontend generates one fresh
    // per "Pay" attempt) — see PaymentServiceImpl.createIntent for why it can't safely be
    // derived on the server from the booking instead.
    @PostMapping("/intent")
    @PreAuthorize("@companyAuth.isBookingOwner(#request.bookingId) or hasAnyRole('ADMIN' , 'RECEPTIONIST') or @companyAuth.hasRoleForBooking(#request.bookingId , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<PaymentIntentResponse> createIntent(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createIntent(request, idempotencyKey));
    }

    // Called by Stripe itself, not a logged-in user — there is no JWT to check. Trust is
    // established instead by verifying the Stripe-Signature header against
    // stripe.webhook-secret inside handleWebhookEvent(); SecurityConfig permits this
    // exact path so the request reaches here unauthenticated in the first place.
    // @RequestBody String, not a DTO: Stripe's signature covers the exact raw bytes, so
    // the payload must reach constructEvent unparsed/unre-serialized.
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                         @RequestHeader("Stripe-Signature") String signature) {
        paymentService.handleWebhookEvent(payload, signature);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@companyAuth.isPaymentOwner(#id) or hasAnyRole('ADMIN' , 'RECEPTIONIST') or @companyAuth.hasRoleForPayment(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<PaymentResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.confirm(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN' , 'RECEPTIONIST') or @companyAuth.hasRoleForPayment(#id , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<PaymentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getById(id));
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("@companyAuth.isBookingOwner(#bookingId) or hasAnyRole('ADMIN' , 'RECEPTIONIST') or @companyAuth.hasRoleForBooking(#bookingId , 'OWNER' , 'MANAGER' , 'RECEPTIONIST')")
    public ResponseEntity<PaymentResponse> getByBookingId(@PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.getByBookingId(bookingId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<PaymentResponse>> getByStatus(@PathVariable PaymentStatus status) {
        return ResponseEntity.ok(paymentService.getByStatus(status));
    }

    @GetMapping("/method/{method}")
    public ResponseEntity<List<PaymentResponse>> getByMethod(@PathVariable PaymentMethod method) {
        return ResponseEntity.ok(paymentService.getByMethod(method));
    }

    @GetMapping("/status/{status}/method/{method}")
    public ResponseEntity<List<PaymentResponse>> getByStatusAndMethod(
            @PathVariable PaymentStatus status,
            @PathVariable PaymentMethod method) {
        return ResponseEntity.ok(paymentService.getByStatusAndMethod(status, method));
    }

    /*
    ADMIN — global check via hasRole('ADMIN'). Any user whose granted
    authorities include ROLE_ADMIN passes, regardless of which
    company/payment it is.


    OWNER / MANAGER — not a global role check. It's scoped:
    hasRoleForPayment(#id, 'OWNER', 'MANAGER') looks up the
    specific payment identified by #id, resolves its owning
    company, and only passes if the current user holds OWNER
    or MANAGER within that company. A user who's MANAGER at a
    different company won't pass for this payment.

    Here we use SpEl - SpEL = Spring Expression Language.
    It's Spring's own expression language (separate from Java)
     that lets you write small logical/dynamic expressions
     as strings, which Spring parses and evaluates at runtime
     — not compile time.
     */

    // amount omitted (or absent from the request entirely) refunds whatever's still
    // owed on this payment; a value refunds exactly that much and must not exceed it —
    // see PaymentServiceImpl.refund for how repeated partial refunds are tracked.
    @PatchMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN') or @companyAuth.hasRoleForPayment(#id , 'OWNER' , 'MANAGER')")
    public ResponseEntity<PaymentResponse> refund(@PathVariable Long id,
                                                   @RequestParam(required = false) Double amount) {
        return ResponseEntity.ok(paymentService.refund(id, amount));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN' , 'SUPPORT') or @companyAuth.hasRoleForPayment(#id , 'OWNER' , 'MANAGER')")
    public ResponseEntity<PaymentResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.cancel(id));
    }
}
