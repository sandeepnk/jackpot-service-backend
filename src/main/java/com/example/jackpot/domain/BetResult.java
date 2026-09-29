package com.example.jackpot.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record BetResult(
    String betId,
    String userId,
    String jackpotId,
    BigDecimal stakeAmount,
    BigDecimal contributionAmount,
    BigDecimal poolAfterContribution,
    BigDecimal poolAfterEvaluation,
    BigDecimal rewardChancePercent,
    boolean won,
    BigDecimal rewardAmount,
    Instant evaluatedAt) {
  public Bet bet() {
    return new Bet(betId, userId, jackpotId, stakeAmount);
  }
}
