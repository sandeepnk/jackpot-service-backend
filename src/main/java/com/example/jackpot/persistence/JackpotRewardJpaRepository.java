package com.example.jackpot.persistence;

import com.example.jackpot.persistence.entity.JackpotRewardEntity;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read access only; ledger inserts are owned by BetRepository. */
public interface JackpotRewardJpaRepository extends Repository<JackpotRewardEntity, String> {
  Optional<JackpotRewardEntity> findById(String betId);
}
