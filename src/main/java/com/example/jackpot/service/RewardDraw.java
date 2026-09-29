package com.example.jackpot.service;

import java.math.BigDecimal;

/** Injectable randomness boundary, allowing deterministic tests. */
@FunctionalInterface
public interface RewardDraw {
  boolean wins(BigDecimal chancePercent);
}
