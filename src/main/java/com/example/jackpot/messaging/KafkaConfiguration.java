package com.example.jackpot.messaging;

import com.example.jackpot.domain.JackpotException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "jackpot.messaging", havingValue = "kafka")
public class KafkaConfiguration {
  public static final String BETS_TOPIC = "jackpot-bets";
  public static final String DEAD_LETTER_TOPIC = "jackpot-bets.DLT";

  @Bean
  NewTopic betsTopic() {
    return TopicBuilder.name(BETS_TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic deadLetterTopic() {
    return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, exception) -> new TopicPartition(DEAD_LETTER_TOPIC, record.partition()));
    // If the DLT write fails, keep the original record eligible for redelivery.
    recoverer.setFailIfSendResultIsError(true);
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    handler.addNotRetryableExceptions(JackpotException.class);
    return handler;
  }
}
