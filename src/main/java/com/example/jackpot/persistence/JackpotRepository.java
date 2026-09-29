package com.example.jackpot.persistence;

import com.example.jackpot.domain.JackpotSnapshot;
import com.example.jackpot.persistence.entity.JackpotEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JackpotRepository {
  private final JackpotJpaRepository jackpotJpaRepository;

  public JackpotRepository(JackpotJpaRepository jackpotJpaRepository) {
    this.jackpotJpaRepository = jackpotJpaRepository;
  }

  public void initialize(String id, BigDecimal initialPool) {
    jackpotJpaRepository.save(new JackpotEntity(id, initialPool, initialPool));
  }

  public Optional<JackpotSnapshot> lock(String id) {
    return jackpotJpaRepository.findByIdForUpdate(id).map(JackpotEntity::toSnapshot);
  }

  public List<JackpotSnapshot> findAll() {
    return jackpotJpaRepository.findAllByOrderByJackpotIdAsc().stream()
        .map(JackpotEntity::toSnapshot)
        .toList();
  }

  public void updatePool(String id, BigDecimal amount) {
    JackpotEntity entity =
        jackpotJpaRepository
            .findById(id)
            .orElseThrow(() -> new IllegalStateException("Jackpot disappeared during processing"));
    entity.setCurrentPool(amount);
    jackpotJpaRepository.save(entity);
  }
}
