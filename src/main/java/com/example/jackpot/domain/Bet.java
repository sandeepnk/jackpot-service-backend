package com.example.jackpot.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** The bet ID is a global idempotency key; IDs are opaque, case-sensitive identifiers. */
public record Bet(
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String betId,
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String userId,
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String jackpotId,
    @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
  public boolean hasSamePayload(Bet other) {
    return betId.equals(other.betId)
        && userId.equals(other.userId)
        && jackpotId.equals(other.jackpotId)
        && amount.compareTo(other.amount) == 0;
  }
}
