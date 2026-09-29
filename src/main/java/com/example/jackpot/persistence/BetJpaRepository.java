package com.example.jackpot.persistence;

import com.example.jackpot.persistence.entity.BetEntity;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read access only; ledger inserts are owned by BetRepository. */
public interface BetJpaRepository extends Repository<BetEntity, String> {
  Optional<BetEntity> findByBetId(String betId);
}
