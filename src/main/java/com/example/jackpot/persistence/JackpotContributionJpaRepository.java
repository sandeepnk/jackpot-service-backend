package com.example.jackpot.persistence;

import com.example.jackpot.persistence.entity.JackpotContributionEntity;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read access only; ledger inserts are owned by BetRepository. */
public interface JackpotContributionJpaRepository
    extends Repository<JackpotContributionEntity, String> {
  Optional<JackpotContributionEntity> findById(String betId);
}
