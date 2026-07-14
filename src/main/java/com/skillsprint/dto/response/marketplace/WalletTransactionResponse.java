package com.skillsprint.dto.response.marketplace;
import com.skillsprint.enums.marketplace.*;
import java.time.Instant; import lombok.*;
@Getter @Builder public class WalletTransactionResponse { WalletTransactionDirection direction; Integer amount; Integer balanceAfter; WalletTransactionReferenceType referenceType; Instant createdAt; }
