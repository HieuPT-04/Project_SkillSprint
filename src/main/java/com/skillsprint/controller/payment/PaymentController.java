package com.skillsprint.controller.payment;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.payment.CreateVnPayPaymentRequest;
import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.dto.response.payment.VnPayIpnResponse;
import com.skillsprint.dto.response.payment.VnPayPaymentUrlResponse;
import com.skillsprint.dto.response.payment.VnPayReturnResponse;
import com.skillsprint.service.payment.VnPayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {

    VnPayPaymentService vnPayPaymentService;

    @PostMapping("/vnpay/create")
    public ResponseEntity<ApiResponse<VnPayPaymentUrlResponse>> createVnPayPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateVnPayPaymentRequest request,
            HttpServletRequest servletRequest
    ) {
        VnPayPaymentUrlResponse response = vnPayPaymentService.createPaymentUrl(
                jwt.getSubject(),
                request,
                getClientIp(servletRequest)
        );
        return ResponseEntity.ok(ApiResponse.success("Tạo yêu cầu thanh toán VNPay thành công", response));
    }

    @GetMapping("/vnpay/ipn")
    public ResponseEntity<VnPayIpnResponse> handleVnPayIpn(@RequestParam Map<String, String> params) {
        VnPayIpnResponse response = vnPayPaymentService.handleIpn(params);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vnpay/return")
    public ResponseEntity<ApiResponse<VnPayReturnResponse>> handleVnPayReturn(
            @RequestParam Map<String, String> params
    ) {
        VnPayReturnResponse response = vnPayPaymentService.handleReturn(params);
        return ResponseEntity.ok(ApiResponse.success("Nhận kết quả thanh toán VNPay thành công", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<PaymentTransactionResponse>>> getMyPayments(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<PaymentTransactionResponse> response = vnPayPaymentService.getMyPayments(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> getMyPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId
    ) {
        PaymentTransactionResponse response = vnPayPaymentService.getMyPayment(jwt.getSubject(), paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}