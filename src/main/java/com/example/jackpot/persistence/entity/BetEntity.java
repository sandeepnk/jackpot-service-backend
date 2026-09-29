package com.example.jackpot.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BetEntity {
  @Id
  @Column(name = "bet_id", nullable = false, length = 64)
  private String betId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "jackpot_id", nullable = false, length = 64)
  private String jackpotId;

  @Column(name = "stake_amount", nullable = false, precision = 24, scale = 2)
  private BigDecimal stakeAmount;

  @Column(name = "won", nullable = false)
  private boolean won;

  @Column(name = "reward_chance_percent", nullable = false, precision = 20, scale = 12)
  private BigDecimal rewardChancePercent;

  @Column(name = "pool_after_evaluation", nullable = false, precision = 24, scale = 2)
  private BigDecimal poolAfterEvaluation;

  @Column(name = "evaluated_at", nullable = false)
  private Instant evaluatedAt;
}
