package com.skillsprint.dto.request.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SepayWebhookRequest {

    Long id;
    String gateway;
    String transactionDate;
    String accountNumber;
    String subAccount;
    String code;
    String content;
    String transferType;
    BigDecimal transferAmount;
    BigDecimal accumulated;
    String referenceCode;
    String description;
}
