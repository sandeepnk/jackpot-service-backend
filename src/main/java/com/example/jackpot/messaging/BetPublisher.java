package com.example.jackpot.messaging;

import com.example.jackpot.domain.Bet;

public interface BetPublisher {
  void publish(Bet bet);
}
