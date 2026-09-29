package com.example.jackpot.domain;

import com.example.jackpot.config.JackpotProperties.Definition;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class VariableContributionPolicy implements ContributionPolicy {
  public String type() {
    return "variable";
  }

  public BigDecimal percentage(Definition definition, BigDecimal currentPool) {
    var configuration = definition.contribution();
    BigDecimal growth = currentPool.subtract(definition.initialPool()).max(BigDecimal.ZERO);
    BigDecimal steps = growth.divideToIntegralValue(configuration.poolStep());
    return configuration
        .initialPercent()
        .subtract(steps.multiply(configuration.decreasePercentPerStep()))
        .max(configuration.minimumPercent());
  }
}
