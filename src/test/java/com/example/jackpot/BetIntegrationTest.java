package com.example.jackpot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.jackpot.domain.Bet;
import com.example.jackpot.domain.JackpotException;
import com.example.jackpot.persistence.BetRepository;
import com.example.jackpot.service.BetProcessor;
import com.example.jackpot.service.RewardDraw;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
class BetIntegrationTest {
  @Autowired BetProcessor processor;
  @Autowired BetRepository bets;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @MockitoBean RewardDraw draw;

  @BeforeEach
  void resetDatabase() {
    jdbc.update("DELETE FROM jackpot_rewards");
    jdbc.update("DELETE FROM jackpot_contributions");
    jdbc.update("DELETE FROM bets");
    jdbc.update("UPDATE jackpots SET current_pool = initial_pool");
    when(draw.wins(any())).thenReturn(false);
  }

  private Bet bet(String id) {
    return new Bet(id, "user-1", "fixed", new BigDecimal("100.00"));
  }

  @Test
  void losingBetContributesAndPersistsAuditFields() {
    var result = processor.process(bet("lose"));
    assertThat(result.won()).isFalse();
    assertThat(result.contributionAmount()).isEqualByComparingTo("5.00");
    assertThat(result.poolAfterContribution()).isEqualByComparingTo("1005.00");
    assertThat(pool("fixed")).isEqualByComparingTo("1005.00");
    assertThat(result.rewardAmount()).isEqualByComparingTo("0");
    assertThat(bets.find("lose")).isPresent();
    assertThat(count("jackpot_contributions")).isEqualTo(1);
    assertThat(count("jackpot_rewards")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT user_id FROM jackpot_contributions WHERE bet_id='lose'", String.class))
        .isEqualTo("user-1");
  }

  @Test
  void winnerReceivesContributedPoolAndResetsOnlyItsJackpot() {
    when(draw.wins(any())).thenReturn(true);
    var result = processor.process(bet("win"));
    assertThat(result.won()).isTrue();
    assertThat(result.rewardAmount()).isEqualByComparingTo("1005.00");
    assertThat(result.poolAfterEvaluation()).isEqualByComparingTo("1000.00");
    assertThat(pool("fixed")).isEqualByComparingTo("1000.00");
    assertThat(pool("variable")).isEqualByComparingTo("1000.00");
    assertThat(count("jackpot_rewards")).isEqualTo(1);
  }

  @Test
  void replayDoesNotContributeOrDrawAgainIncludingEquivalentDecimalScale() {
    when(draw.wins(any())).thenReturn(true);
    processor.process(bet("duplicate"));
    var replay = processor.process(new Bet("duplicate", "user-1", "fixed", new BigDecimal("100")));
    assertThat(replay.rewardAmount()).isEqualByComparingTo("1005");
    verify(draw, times(1)).wins(any());
    assertThat(count("bets")).isEqualTo(1);
    assertThat(count("jackpot_rewards")).isEqualTo(1);
  }

  @Test
  void conflictingIdIsRejectedAcrossJackpots() {
    processor.process(bet("conflict"));
    assertThatThrownBy(
            () -> processor.process(new Bet("conflict", "user-2", "variable", BigDecimal.ONE)))
        .isInstanceOf(JackpotException.class)
        .hasMessageContaining("different payload");
    assertThat(pool("variable")).isEqualByComparingTo("1000");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void concurrentCrossJackpotConflictNeverOverwritesCommittedLedger(boolean winningBet)
      throws Exception {
    var reachedDraw = new CountDownLatch(1);
    var releaseDraw = new CountDownLatch(1);
    when(draw.wins(any()))
        .thenAnswer(
            invocation -> {
              if (Thread.currentThread().getName().equals("conflicting-bet")) {
                reachedDraw.countDown();
                if (!releaseDraw.await(10, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("Timed out waiting for first bet to commit");
                }
              }
              return winningBet;
            });

    try (var executor =
        Executors.newSingleThreadExecutor(task -> new Thread(task, "conflicting-bet"))) {
      var conflicting =
          executor.submit(
              () ->
                  processor.process(
                      new Bet(
                          "cross-pool-race", "other-user", "variable", new BigDecimal("100.00"))));
      try {
        assertThat(reachedDraw.await(10, TimeUnit.SECONDS)).isTrue();
        // Both ID checks in the competing transaction have passed, but its writes are paused.
        processor.process(bet("cross-pool-race"));
      } finally {
        releaseDraw.countDown();
      }
      assertThatThrownBy(() -> conflicting.get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    var persisted = bets.find("cross-pool-race").orElseThrow();
    assertThat(persisted.userId()).isEqualTo("user-1");
    assertThat(persisted.jackpotId()).isEqualTo("fixed");
    assertThat(persisted.contributionAmount()).isEqualByComparingTo("5.00");
    assertThat(persisted.won()).isEqualTo(winningBet);
    assertThat(persisted.rewardAmount()).isEqualByComparingTo(winningBet ? "1005.00" : "0.00");
    assertThat(pool("fixed")).isEqualByComparingTo(winningBet ? "1000.00" : "1005.00");
    assertThat(pool("variable")).isEqualByComparingTo("1000.00");
    assertThat(count("bets")).isEqualTo(1);
    assertThat(count("jackpot_contributions")).isEqualTo(1);
    assertThat(count("jackpot_rewards")).isEqualTo(winningBet ? 1 : 0);
  }

  @Test
  void failedEvaluationRollsBackAllWrites() {
    when(draw.wins(any())).thenThrow(new IllegalStateException("simulated draw failure"));
    assertThatThrownBy(() -> processor.process(bet("rollback")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(pool("fixed")).isEqualByComparingTo("1000");
    assertThat(count("bets")).isZero();
    assertThat(count("jackpot_contributions")).isZero();
  }

  @Test
  void failedLedgerInsertRollsBackBetAndPool() {
    // Force a database failure after the bet insert, proving transaction rollback across ledger
    // writes.
    jdbc.execute(
        "ALTER TABLE jackpot_contributions ADD CONSTRAINT test_reject CHECK (stake_amount < 50)");
    try {
      assertThatThrownBy(() -> processor.process(bet("rollback-ledger")))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(count("bets")).isZero();
      assertThat(pool("fixed")).isEqualByComparingTo("1000");
    } finally {
      jdbc.execute("ALTER TABLE jackpot_contributions DROP CONSTRAINT test_reject");
    }
  }

  @Test
  void moneyIsRoundedHalfUpAtContributionBoundary() {
    var result = processor.process(new Bet("round", "user", "fixed", new BigDecimal("0.10")));
    assertThat(result.contributionAmount()).isEqualByComparingTo("0.01");
  }

  @Test
  void concurrentDistinctBetsNeverLoseContributions() throws Exception {
    runConcurrently(40, index -> processor.process(bet("concurrent-" + index)));
    assertThat(count("bets")).isEqualTo(40);
    assertThat(pool("fixed")).isEqualByComparingTo("1200");
  }

  @Test
  void concurrentDuplicateDeliveriesHaveExactlyOneEffect() throws Exception {
    runConcurrently(20, index -> processor.process(bet("same")));
    assertThat(count("bets")).isEqualTo(1);
    assertThat(pool("fixed")).isEqualByComparingTo("1005");
    verify(draw, times(1)).wins(any());
  }

  @Test
  void apiPublishesAndReturnsStableEvaluation() throws Exception {
    mvc.perform(
            post("/api/v1/bets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("api", "100.00")))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Location", "/api/v1/bets/api"));
    mvc.perform(post("/api/v1/bets/api/evaluation"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("contributionAmount").value(5))
        .andExpect(jsonPath("won").value(false));
    mvc.perform(get("/api/v1/bets/api"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("rewardAmount").value(0));
    mvc.perform(post("/api/v1/bets/api/evaluation")).andExpect(status().isOk());
    verify(draw, times(1)).wins(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "0.001", "1000000000000", "null"})
  void invalidAmountsAreRejected(String amount) throws Exception {
    mvc.perform(
            post("/api/v1/bets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("invalid", amount)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    assertThat(count("bets")).isZero();
  }

  @Test
  void invalidIdsAndMalformedJsonAreRejected() throws Exception {
    mvc.perform(
            post("/api/v1/bets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("bad/id", "10")))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/bets").contentType(MediaType.APPLICATION_JSON).content("{broken"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/bets").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unknownJackpotAndUnprocessedBetReturnNotFound() throws Exception {
    mvc.perform(
            post("/api/v1/bets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("missing", "10").replace("fixed", "unknown")))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/bets/unknown/evaluation"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("code").value("BET_NOT_PROCESSED"));
  }

  @Test
  void apiRejectsPayloadConflict() throws Exception {
    processor.process(bet("conflict"));
    mvc.perform(
            post("/api/v1/bets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("conflict", "200")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("code").value("BET_ID_CONFLICT"));
  }

  @Test
  void consumerBoundaryAlsoValidatesPayload() {
    assertThatThrownBy(
            () -> processor.process(new Bet("bad", "user", "fixed", new BigDecimal("-1"))))
        .isInstanceOf(JackpotException.class);
    assertThatThrownBy(() -> processor.process(null)).isInstanceOf(JackpotException.class);
  }

  private String payload(String id, String amount) {
    return """
                {"betId":"%s","userId":"user-1","jackpotId":"fixed","amount":%s}
                """
        .formatted(id, amount);
  }

  private BigDecimal pool(String id) {
    return jdbc.queryForObject(
        "SELECT current_pool FROM jackpots WHERE jackpot_id = ?", BigDecimal.class, id);
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  private void runConcurrently(int count, java.util.function.IntConsumer action) throws Exception {
    try (var executor = Executors.newFixedThreadPool(8)) {
      var futures = new ArrayList<Future<?>>();
      for (int i = 0; i < count; i++) {
        final int index = i;
        futures.add(executor.submit(() -> action.accept(index)));
      }
      for (var future : futures) {
        future.get(20, TimeUnit.SECONDS);
      }
    }
  }
}
