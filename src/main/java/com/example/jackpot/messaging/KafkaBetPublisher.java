package com.example.jackpot.messaging;

import com.example.jackpot.domain.Bet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jackpot.messaging", havingValue = "kafka")
public class KafkaBetPublisher implements BetPublisher {
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper mapper;

  public KafkaBetPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
    this.kafka = kafka;
    this.mapper = mapper;
  }

  @Override
  public void publish(Bet bet) {
    try {
      // Keying by jackpot preserves the order of bets for each pool.
      kafka
          .send(KafkaConfiguration.BETS_TOPIC, bet.jackpotId(), mapper.writeValueAsString(bet))
          .get(12, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new PublicationException(
          "Publication interrupted; retry with the same bet ID", exception);
    } catch (ExecutionException | TimeoutException | JsonProcessingException exception) {
      throw new PublicationException(
          "Publication not confirmed; retry with the same bet ID", exception);
    } catch (org.apache.kafka.common.KafkaException
        | org.springframework.kafka.KafkaException exception) {
      throw new PublicationException("Kafka unavailable; retry with the same bet ID", exception);
    }
  }
}
