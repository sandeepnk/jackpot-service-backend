package com.example.jackpot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.jackpot.config.JackpotProperties;
import com.example.jackpot.config.JackpotProperties.Contribution;
import com.example.jackpot.config.JackpotProperties.Definition;
import com.example.jackpot.config.JackpotProperties.Reward;
import com.example.jackpot.domain.FixedContributionPolicy;
import com.example.jackpot.domain.FixedRewardPolicy;
import com.example.jackpot.domain.VariableContributionPolicy;
import com.example.jackpot.domain.VariableRewardPolicy;
import com.example.jackpot.service.PolicyRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PolicyTest {
  private static BigDecimal decimal(String value) {
    return new BigDecimal(value);
  }

  static Definition definition(String contributionType, String rewardType) {
    return new Definition(
        "test",
        decimal("1000"),
        new Contribution(
            contributionType, decimal("10"), decimal("1"), decimal("100"), decimal("1")),
        new Reward(rewardType, decimal("10"), decimal("2000")));
  }

  @ParameterizedTest
  @CsvSource({"1000,10", "1099.99,10", "1100,9", "1500,5", "1900,1", "10000,1", "900,10"})
  void variableContributionDecreasesInStepsAndStopsAtFloor(String pool, String expected) {
    assertThat(
            new VariableContributionPolicy()
                .percentage(definition("variable", "variable"), decimal(pool)))
        .isEqualByComparingTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"900,10", "1000,10", "1500,55", "1999,99.91", "2000,100", "2001,100"})
  void variableRewardGrowsLinearlyAndIsGuaranteedAtLimit(String pool, String expected) {
    assertThat(
            new VariableRewardPolicy()
                .chancePercent(definition("variable", "variable"), decimal(pool)))
        .isEqualByComparingTo(expected);
  }

  @Test
  void fixedPoliciesIgnorePoolSize() {
    var definition = definition("fixed", "fixed");
    assertThat(new FixedContributionPolicy().percentage(definition, decimal("999999")))
        .isEqualByComparingTo("10");
    assertThat(new FixedRewardPolicy().chancePercent(definition, decimal("999999")))
        .isEqualByComparingTo("10");
  }

  @Test
  void unknownPolicyFailsAtStartup() {
    assertThatThrownBy(() -> registry(List.of(definition("unknown", "fixed"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown policy");
  }

  @Test
  void duplicateJackpotIdsFailAtStartup() {
    assertThatThrownBy(
            () -> registry(List.of(definition("fixed", "fixed"), definition("fixed", "fixed"))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void invalidRewardLimitFailsAtStartup() {
    var definition = definition("variable", "variable");
    var invalid =
        new Definition("test", decimal("2000"), definition.contribution(), definition.reward());
    assertThatThrownBy(() -> registry(List.of(invalid)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit must exceed");
  }

  private PolicyRegistry registry(List<Definition> definitions) {
    return new PolicyRegistry(
        new JackpotProperties(definitions),
        List.of(new FixedContributionPolicy(), new VariableContributionPolicy()),
        List.of(new FixedRewardPolicy(), new VariableRewardPolicy()));
  }
}
