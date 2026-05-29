package com.skillsprint.service.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class VnPaySigner {

    private static final String HMAC_SHA512 = "HmacSHA512";

    public String buildPaymentUrl(String payUrl, Map<String, String> params, String hashSecret) {
        Map<String, String> sortedParams = sanitizeParams(params);

        String hashData = buildQueryString(sortedParams);
        String secureHash = hmacSha512(hashSecret, hashData);
        String query = buildQueryString(sortedParams);

        return payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    public boolean verify(Map<String, String> params, String hashSecret) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }

        Map<String, String> sortedParams = sanitizeParams(params);
        String hashData = buildQueryString(sortedParams);
        String calculatedHash = hmacSha512(hashSecret, hashData);

        return receivedHash.equalsIgnoreCase(calculatedHash);
    }

    private Map<String, String> sanitizeParams(Map<String, String> params) {
        return params.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .filter(entry -> !"vnp_SecureHashType".equals(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> right,
                        TreeMap::new
                ));
    }

    private String buildQueryString(Map<String, String> sortedParams) {
        return sortedParams.entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String hmacSha512(String secret, String data) {
        try {
            Mac hmac512 = Mac.getInstance(HMAC_SHA512);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
            hmac512.init(secretKey);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create VNPay secure hash", ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}