package com.example.jackpot.domain;

import com.example.jackpot.config.JackpotProperties.Definition;
import java.math.BigDecimal;

public interface RewardPolicy {
  String type();

  BigDecimal chancePercent(Definition definition, BigDecimal contributedPool);
}
