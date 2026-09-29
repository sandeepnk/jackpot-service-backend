package com.example.jackpot.persistence;

import com.example.jackpot.domain.BetResult;
import com.example.jackpot.persistence.entity.BetEntity;
import com.example.jackpot.persistence.entity.JackpotContributionEntity;
import com.example.jackpot.persistence.entity.JackpotRewardEntity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BetRepository {
  private final BetJpaRepository betJpaRepository;
  private final JackpotContributionJpaRepository jackpotContributionJpaRepository;
  private final JackpotRewardJpaRepository jackpotRewardJpaRepository;
  private final EntityManager entityManager;

  public BetRepository(
      BetJpaRepository betJpaRepository,
      JackpotContributionJpaRepository jackpotContributionJpaRepository,
      JackpotRewardJpaRepository jackpotRewardJpaRepository,
      EntityManager entityManager) {
    this.betJpaRepository = betJpaRepository;
    this.jackpotContributionJpaRepository = jackpotContributionJpaRepository;
    this.jackpotRewardJpaRepository = jackpotRewardJpaRepository;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public Optional<BetResult> find(String betId) {
    return betJpaRepository.findByBetId(betId).map(this::toBetResult);
  }

  /** Ledger records are insert-only: a concurrent ID collision must roll back, never merge. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void save(BetResult result) {
    entityManager.persist(
        new BetEntity(
            result.betId(),
            result.userId(),
            result.jackpotId(),
            result.stakeAmount(),
            result.won(),
            result.rewardChancePercent(),
            result.poolAfterEvaluation(),
            result.evaluatedAt()));

    entityManager.persist(
        new JackpotContributionEntity(
            result.betId(),
            result.userId(),
            result.jackpotId(),
            result.stakeAmount(),
            result.contributionAmount(),
            result.poolAfterContribution(),
            result.evaluatedAt()));

    if (result.won()) {
      entityManager.persist(
          new JackpotRewardEntity(
              result.betId(),
              result.userId(),
              result.jackpotId(),
              result.rewardAmount(),
              result.evaluatedAt()));
    }
    // Surface constraint failures within the repository's exception-translation boundary.
    entityManager.flush();
  }

  private BetResult toBetResult(BetEntity bet) {
    JackpotContributionEntity contribution =
        jackpotContributionJpaRepository
            .findById(bet.getBetId())
            .orElseThrow(
                () -> new IllegalStateException("Processed bet is missing its contribution"));
    BigDecimal rewardAmount =
        bet.isWon()
            ? jackpotRewardJpaRepository
                .findById(bet.getBetId())
                .orElseThrow(() -> new IllegalStateException("Winning bet is missing its reward"))
                .getJackpotRewardAmount()
            : BigDecimal.ZERO.setScale(2);

    return new BetResult(
        bet.getBetId(),
        bet.getUserId(),
        bet.getJackpotId(),
        bet.getStakeAmount(),
        contribution.getContributionAmount(),
        contribution.getCurrentJackpotAmount(),
        bet.getPoolAfterEvaluation(),
        bet.getRewardChancePercent(),
        bet.isWon(),
        rewardAmount,
        bet.getEvaluatedAt());
  }
}
