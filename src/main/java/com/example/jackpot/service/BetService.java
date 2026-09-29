package com.example.jackpot.service;

import com.example.jackpot.domain.Bet;
import com.example.jackpot.domain.BetResult;
import com.example.jackpot.domain.JackpotException;
import com.example.jackpot.messaging.BetPublisher;
import com.example.jackpot.persistence.BetRepository;
import org.springframework.stereotype.Service;

@Service
public class BetService {
  private final BetRepository bets;
  private final BetPublisher publisher;
  private final PolicyRegistry policies;

  public BetService(BetRepository bets, BetPublisher publisher, PolicyRegistry policies) {
    this.bets = bets;
    this.publisher = publisher;
    this.policies = policies;
  }

  public void submit(Bet bet) {
    policies.definition(bet.jackpotId());
    var existing = bets.find(bet.betId());
    if (existing.isPresent()) {
      BetProcessor.requireSamePayload(bet, existing.get());
      return;
    }
    publisher.publish(bet);
  }

  public BetResult evaluate(String betId) {
    return bets.find(betId)
        .orElseThrow(
            () ->
                new JackpotException(
                    JackpotException.Code.BET_NOT_PROCESSED,
                    "Bet has not been processed or does not exist; retry after publishing"));
  }
}
