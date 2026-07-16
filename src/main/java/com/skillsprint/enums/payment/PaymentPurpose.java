package com.skillsprint.enums.payment;

/**
 * Why a {@code payment_transactions} row exists. A payment is created for exactly one
 * purpose and can never be repurposed: a SUBSCRIPTION webhook must not credit Coin and
 * a COIN_TOP_UP webhook must not touch subscription state.
 */
public enum PaymentPurpose {
    SUBSCRIPTION,
    COIN_TOP_UP
}
