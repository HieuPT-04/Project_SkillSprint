package com.skillsprint.dto.request.marketplace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * A top-up request may only name a server-configured package.
 *
 * <p>There is deliberately no user, VND, or Coin field: the buyer comes from the JWT
 * and the amounts come from {@code app.payment.coin.packages}. Jackson runs with
 * FAIL_ON_UNKNOWN_PROPERTIES = false, so a client that tries to send {@code userId} or
 * {@code vndAmount} has those fields silently dropped rather than honoured.
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateCoinTopUpRequest {

    @NotBlank(message = "Cần chọn gói nạp Coin")
    @Size(max = 50)
    String packageKey;
}
