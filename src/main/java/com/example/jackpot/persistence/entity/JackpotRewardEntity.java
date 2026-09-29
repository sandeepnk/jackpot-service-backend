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
@Table(name = "jackpot_rewards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class JackpotRewardEntity {
  @Id
  @Column(name = "bet_id", nullable = false, length = 64)
  private String betId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "jackpot_id", nullable = false, length = 64)
  private String jackpotId;

  @Column(name = "jackpot_reward_amount", nullable = false, precision = 24, scale = 2)
  private BigDecimal jackpotRewardAmount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
