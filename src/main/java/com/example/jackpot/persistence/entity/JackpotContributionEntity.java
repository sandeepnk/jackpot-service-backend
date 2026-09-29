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
@Table(name = "jackpot_contributions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class JackpotContributionEntity {
  @Id
  @Column(name = "bet_id", nullable = false, length = 64)
  private String betId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "jackpot_id", nullable = false, length = 64)
  private String jackpotId;

  @Column(name = "stake_amount", nullable = false, precision = 24, scale = 2)
  private BigDecimal stakeAmount;

  @Column(name = "contribution_amount", nullable = false, precision = 24, scale = 2)
  private BigDecimal contributionAmount;

  @Column(name = "current_jackpot_amount", nullable = false, precision = 24, scale = 2)
  private BigDecimal currentJackpotAmount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
