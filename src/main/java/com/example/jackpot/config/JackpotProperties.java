package com.example.jackpot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jackpot")
public record JackpotProperties(@NotEmpty List<@Valid Definition> definitions) {
  public record Definition(
      @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String id,
      @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal initialPool,
      @NotNull @Valid Contribution contribution,
      @NotNull @Valid Reward reward) {}

  public record Contribution(
      @NotBlank String type,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal initialPercent,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minimumPercent,
      @NotNull @DecimalMin("0.01") BigDecimal poolStep,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal decreasePercentPerStep) {
    @AssertTrue(message = "minimum contribution must not exceed initial contribution")
    public boolean isRangeValid() {
      return initialPercent == null
          || minimumPercent == null
          || minimumPercent.compareTo(initialPercent) <= 0;
    }
  }

  public record Reward(
      @NotBlank String type,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal initialChancePercent,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal poolLimit) {}
}
