package com.example.jackpot.domain;

import com.example.jackpot.config.JackpotProperties.Definition;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class FixedContributionPolicy implements ContributionPolicy {
  public String type() {
    return "fixed";
  }

  public BigDecimal percentage(Definition definition, BigDecimal currentPool) {
    return definition.contribution().initialPercent();
  }
}
