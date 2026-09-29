package com.example.jackpot.domain;

import com.example.jackpot.config.JackpotProperties.Definition;
import java.math.BigDecimal;

public interface ContributionPolicy {
  String type();

  BigDecimal percentage(Definition definition, BigDecimal currentPool);
}
