package com.example.jackpot.persistence.entity;

import com.example.jackpot.domain.JackpotSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "jackpots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class JackpotEntity {
  @Id
  @Column(name = "jackpot_id", nullable = false, length = 64)
  private String jackpotId;

  @Column(name = "initial_pool", nullable = false, precision = 24, scale = 2)
  private BigDecimal initialPool;

  @Setter
  @Column(name = "current_pool", nullable = false, precision = 24, scale = 2)
  private BigDecimal currentPool;

  public JackpotSnapshot toSnapshot() {
    return new JackpotSnapshot(jackpotId, initialPool, currentPool);
  }
}
