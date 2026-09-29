package com.example.jackpot.domain;

import com.example.jackpot.config.JackpotProperties.Definition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class VariableRewardPolicy implements RewardPolicy {
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  public String type() {
    return "variable";
  }

  public BigDecimal chancePercent(Definition definition, BigDecimal contributedPool) {
    var configuration = definition.reward();
    if (contributedPool.compareTo(configuration.poolLimit()) >= 0) {
      return HUNDRED;
    }
    BigDecimal growth = contributedPool.subtract(definition.initialPool()).max(BigDecimal.ZERO);
    BigDecimal range = configuration.poolLimit().subtract(definition.initialPool());
    BigDecimal increase =
        HUNDRED
            .subtract(configuration.initialChancePercent())
            .multiply(growth)
            .divide(range, 12, RoundingMode.HALF_UP);
    return configuration.initialChancePercent().add(increase).min(HUNDRED);
  }
}
