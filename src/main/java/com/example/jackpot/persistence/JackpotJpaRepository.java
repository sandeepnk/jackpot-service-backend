package com.example.jackpot.persistence;

import com.example.jackpot.persistence.entity.JackpotEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface JackpotJpaRepository extends JpaRepository<JackpotEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from JackpotEntity j where j.jackpotId = :id")
  Optional<JackpotEntity> findByIdForUpdate(String id);

  List<JackpotEntity> findAllByOrderByJackpotIdAsc();
}
