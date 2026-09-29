package com.example.jackpot.messaging;

import com.example.jackpot.domain.Bet;
import com.example.jackpot.domain.JackpotException;
import com.example.jackpot.service.BetProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@DependsOn("jackpotInitializer")
@ConditionalOnProperty(name = "jackpot.messaging", havingValue = "kafka")
public class KafkaBetListener {
  private final ObjectMapper mapper;
  private final BetProcessor processor;

  public KafkaBetListener(ObjectMapper mapper, BetProcessor processor) {
    this.mapper = mapper;
    this.processor = processor;
  }

  @KafkaListener(topics = KafkaConfiguration.BETS_TOPIC)
  public void receive(String payload) {
    try {
      processor.process(mapper.readValue(payload, Bet.class));
    } catch (JsonProcessingException exception) {
      throw new JackpotException(JackpotException.Code.INVALID_BET, "Malformed bet event");
    }
  }
}
