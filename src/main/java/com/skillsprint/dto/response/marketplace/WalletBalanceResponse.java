package com.skillsprint.dto.response.marketplace;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class WalletBalanceResponse { String userId; Integer balance; }
