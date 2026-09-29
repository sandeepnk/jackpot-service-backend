package com.example.jackpot.service;

import com.example.jackpot.domain.Bet;
import com.example.jackpot.domain.BetResult;
import com.example.jackpot.domain.JackpotException;
import com.example.jackpot.persistence.BetRepository;
import com.example.jackpot.persistence.JackpotRepository;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BetProcessor {
  private final BetRepository bets;
  private final JackpotRepository jackpots;
  private final PolicyRegistry policies;
  private final RewardDraw draw;
  private final Validator validator;
  private final Clock clock;

  public BetProcessor(
      BetRepository bets,
      JackpotRepository jackpots,
      PolicyRegistry policies,
      RewardDraw draw,
      Validator validator,
      Clock clock) {
    this.bets = bets;
    this.jackpots = jackpots;
    this.policies = policies;
    this.draw = draw;
    this.validator = validator;
    this.clock = clock;
  }

  /** A pool lock serializes contributions and payouts; all ledger writes commit together. */
  @Transactional
  public BetResult process(Bet bet) {
    validate(bet);
    var existing = bets.find(bet.betId());
    if (existing.isPresent()) {
      return requireSamePayload(bet, existing.get());
    }
    var definition = policies.definition(bet.jackpotId());
    var jackpot =
        jackpots
            .lock(bet.jackpotId())
            .orElseThrow(
                () ->
                    new JackpotException(
                        JackpotException.Code.JACKPOT_NOT_FOUND, "Jackpot does not exist"));
    // A concurrent delivery may have committed while this transaction waited for the pool lock.
    existing = bets.find(bet.betId());
    if (existing.isPresent()) {
      return requireSamePayload(bet, existing.get());
    }

    BigDecimal contribution =
        bet.amount()
            .multiply(
                policies.contribution(definition).percentage(definition, jackpot.currentPool()))
            .movePointLeft(2)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal contributedPool = jackpot.currentPool().add(contribution);
    BigDecimal chance = policies.reward(definition).chancePercent(definition, contributedPool);
    boolean won = draw.wins(chance);
    BigDecimal finalPool = won ? jackpot.initialPool() : contributedPool;
    BetResult result =
        new BetResult(
            bet.betId(),
            bet.userId(),
            bet.jackpotId(),
            bet.amount(),
            contribution,
            contributedPool,
            finalPool,
            chance,
            won,
            won ? contributedPool : new BigDecimal("0.00"),
            clock.instant());
    bets.save(result);
    jackpots.updatePool(bet.jackpotId(), finalPool);
    return result;
  }

  public void validate(Bet bet) {
    if (bet == null || !validator.validate(bet).isEmpty()) {
      throw new JackpotException(
          JackpotException.Code.INVALID_BET,
          "Bet IDs and positive amount with at most two decimal places are required");
    }
  }

  public static BetResult requireSamePayload(Bet bet, BetResult existing) {
    if (!existing.bet().hasSamePayload(bet)) {
      throw new JackpotException(
          JackpotException.Code.BET_ID_CONFLICT,
          "Bet ID is already associated with a different payload");
    }
    return existing;
  }
}
