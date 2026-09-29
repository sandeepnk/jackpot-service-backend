package com.example.jackpot.domain;

import com.example.jackpot.config.JackpotProperties.Definition;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class FixedRewardPolicy implements RewardPolicy {
  public String type() {
    return "fixed";
  }

  public BigDecimal chancePercent(Definition definition, BigDecimal contributedPool) {
    return definition.reward().initialChancePercent();
  }
}
