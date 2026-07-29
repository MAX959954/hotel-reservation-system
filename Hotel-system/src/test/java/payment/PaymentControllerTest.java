package payment;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@ContextConfiguration(classes = {MinimalTestApplication.class, PaymentController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private PaymentResponse sampleResponse() {
        return PaymentResponse.builder()
                .id(1L)
                .bookingId(1L)
                .amount(200.0)
                .method(PaymentMethod.CREDIT_CARD)
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    private PaymentRequest validRequest() {
        PaymentRequest request = new PaymentRequest();
        request.setBookingId(1L);
        request.setMethod(PaymentMethod.CREDIT_CARD);
        request.setCurrency("USD");
        return request;
    }

    @Test
    void pay_returns201_whenRequestValid() throws Exception {
        given(paymentService.pay(any(PaymentRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void pay_returns400_whenCurrencyBlank() throws Exception {
        PaymentRequest invalid = validRequest();
        invalid.setCurrency("");

        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200_whenFound() throws Exception {
        given(paymentService.getById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/payments/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(200.0));
    }

    @Test
    void getById_returns400_whenNotFound() throws Exception {
        given(paymentService.getById(1L)).willThrow(new IllegalStateException("Payment not found"));

        mockMvc.perform(get("/api/payments/{id}", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByStatus_returns200() throws Exception {
        given(paymentService.getByStatus(PaymentStatus.COMPLETED)).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/payments/status/{status}", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void refund_returns200() throws Exception {
        PaymentResponse refunded = sampleResponse();
        refunded.setStatus(PaymentStatus.REFUNDED);
        given(paymentService.refund(anyLong())).willReturn(refunded);

        mockMvc.perform(patch("/api/payments/{id}/refund", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void cancel_returns200() throws Exception {
        PaymentResponse cancelled = sampleResponse();
        cancelled.setStatus(PaymentStatus.CANCELLED);
        given(paymentService.cancel(anyLong())).willReturn(cancelled);

        mockMvc.perform(patch("/api/payments/{id}/cancel", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_returns400_whenServiceThrows() throws Exception {
        given(paymentService.cancel(1L)).willThrow(new IllegalStateException("Payment cannot be cancelled"));

        mockMvc.perform(patch("/api/payments/{id}/cancel", 1L))
                .andExpect(status().isBadRequest());
    }
}
