package com.example.jackpot.service;

import com.example.jackpot.config.JackpotProperties;
import com.example.jackpot.config.JackpotProperties.Definition;
import com.example.jackpot.domain.ContributionPolicy;
import com.example.jackpot.domain.JackpotException;
import com.example.jackpot.domain.RewardPolicy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PolicyRegistry {
  private final Map<String, Definition> definitions;
  private final Map<String, ContributionPolicy> contributions;
  private final Map<String, RewardPolicy> rewards;

  public PolicyRegistry(
      JackpotProperties properties,
      List<ContributionPolicy> contributions,
      List<RewardPolicy> rewards) {
    this.definitions =
        properties.definitions().stream()
            .collect(Collectors.toUnmodifiableMap(Definition::id, Function.identity()));
    this.contributions =
        contributions.stream()
            .collect(Collectors.toUnmodifiableMap(ContributionPolicy::type, Function.identity()));
    this.rewards =
        rewards.stream()
            .collect(Collectors.toUnmodifiableMap(RewardPolicy::type, Function.identity()));
    definitions
        .values()
        .forEach(
            definition -> {
              if (!this.contributions.containsKey(definition.contribution().type())
                  || !this.rewards.containsKey(definition.reward().type())) {
                throw new IllegalArgumentException("Unknown policy for jackpot " + definition.id());
              }
              if (definition.reward().type().equals("variable")
                  && definition.reward().poolLimit().compareTo(definition.initialPool()) <= 0) {
                throw new IllegalArgumentException(
                    "Variable reward limit must exceed initial pool for " + definition.id());
              }
            });
  }

  public Definition definition(String id) {
    Definition definition = definitions.get(id);
    if (definition == null) {
      throw new JackpotException(
          JackpotException.Code.JACKPOT_NOT_FOUND, "Jackpot does not exist: " + id);
    }
    return definition;
  }

  public ContributionPolicy contribution(Definition definition) {
    return contributions.get(definition.contribution().type());
  }

  public RewardPolicy reward(Definition definition) {
    return rewards.get(definition.reward().type());
  }
}
