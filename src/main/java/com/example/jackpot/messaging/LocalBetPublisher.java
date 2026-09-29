package com.example.jackpot.messaging;

import com.example.jackpot.domain.Bet;
import com.example.jackpot.service.BetProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Broker-free assignment mode; uses the exact same transactional processing path. */
@Component
@ConditionalOnProperty(name = "jackpot.messaging", havingValue = "local", matchIfMissing = true)
public class LocalBetPublisher implements BetPublisher {
  private static final Logger log = LoggerFactory.getLogger(LocalBetPublisher.class);
  private final BetProcessor processor;

  public LocalBetPublisher(BetProcessor processor) {
    this.processor = processor;
  }

  @Override
  public void publish(Bet bet) {
    log.info("Local publication topic=jackpot-bets payload={}", bet);
    processor.process(bet);
  }
}
