package com.skillsprint.dto.response.marketplace;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/** Current balance and recent immutable ledger entries for an administrator. */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminWalletResponse {

    String userId;
    Integer balance;
    List<AdminWalletTransactionResponse> recentTransactions;
}
