package com.skillsprint.controller.payment;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.payment.CreateSepayPaymentRequest;
import com.skillsprint.dto.request.payment.SepayWebhookRequest;
import com.skillsprint.dto.response.payment.SepayPaymentResponse;
import com.skillsprint.dto.response.payment.SepayWebhookResponse;
import com.skillsprint.dto.response.payment.UserPaymentResponse;
import com.skillsprint.service.payment.SepayPaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {

    SepayPaymentService sepayPaymentService;

    @PostMapping("/sepay/create")
    public ResponseEntity<ApiResponse<SepayPaymentResponse>> createSepayPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSepayPaymentRequest request
    ) {
        SepayPaymentResponse response = sepayPaymentService.createPayment(
                jwt.getSubject(),
                request
        );
        return ResponseEntity.ok(ApiResponse.success("Tạo thanh toán thành công", response));
    }

    @PostMapping("/sepay/webhook")
    public ResponseEntity<SepayWebhookResponse> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKeyHeader,
            @RequestBody SepayWebhookRequest request
    ) {
        sepayPaymentService.handleWebhook(request, authorizationHeader, apiKeyHeader);
        return ResponseEntity.ok(new SepayWebhookResponse(true));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<UserPaymentResponse>>> getMyPayments(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<UserPaymentResponse> response = sepayPaymentService.getMyPayments(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử thanh toán thành công", response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<UserPaymentResponse>> getMyPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId
    ) {
        UserPaymentResponse response = sepayPaymentService.getMyPayment(jwt.getSubject(), paymentId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thanh toán thành công", response));
    }
}
