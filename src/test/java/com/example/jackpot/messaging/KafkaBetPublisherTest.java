package com.example.jackpot.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.jackpot.api.ApiExceptionHandler;
import com.example.jackpot.api.BetController;
import com.example.jackpot.domain.Bet;
import com.example.jackpot.persistence.BetRepository;
import com.example.jackpot.service.BetService;
import com.example.jackpot.service.PolicyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KafkaBetPublisherTest {
  private static final Bet BET = new Bet("publish-test", "user", "fixed", new BigDecimal("100.00"));
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void publishesJsonUsingJackpotAsPartitionKey() throws Exception {
    try (var producer =
        new MockProducer<String, String>(true, new StringSerializer(), new StringSerializer())) {
      ProducerFactory<String, String> factory = () -> producer;
      new KafkaBetPublisher(new KafkaTemplate<>(factory), mapper).publish(BET);
      assertThat(producer.history()).hasSize(1);
      var record = producer.history().getFirst();
      assertThat(record.topic()).isEqualTo(KafkaConfiguration.BETS_TOPIC);
      assertThat(record.key()).isEqualTo(BET.jackpotId());
      assertThat(mapper.readValue(record.value(), Bet.class)).isEqualTo(BET);
    }
  }

  @Test
  void translatesSpringKafkaSynchronousFailureFromRealTemplate() {
    try (var producer = failingProducer()) {
      ProducerFactory<String, String> factory = () -> producer;
      var publisher = new KafkaBetPublisher(new KafkaTemplate<>(factory), mapper);
      assertThatThrownBy(() -> publisher.publish(BET))
          .isInstanceOf(PublicationException.class)
          .hasCauseInstanceOf(org.springframework.kafka.KafkaException.class)
          .hasMessageContaining("same bet ID");
    }
  }

  @Test
  void translatesApacheKafkaSynchronousFailure() {
    var template = template();
    when(template.send(anyString(), anyString(), anyString()))
        .thenThrow(new TimeoutException("unavailable"));
    assertThatThrownBy(() -> new KafkaBetPublisher(template, mapper).publish(BET))
        .isInstanceOf(PublicationException.class)
        .hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void translatesAsynchronousFailure() {
    var template = template();
    when(template.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new TimeoutException("delivery timeout")));
    assertThatThrownBy(() -> new KafkaBetPublisher(template, mapper).publish(BET))
        .isInstanceOf(PublicationException.class)
        .hasMessageContaining("same bet ID");
  }

  @Test
  void preservesInterruptFlagWhenPublicationIsInterrupted() {
    var template = template();
    when(template.send(anyString(), anyString(), anyString()))
        .thenReturn(new CompletableFuture<SendResult<String, String>>());
    try {
      Thread.currentThread().interrupt();
      assertThatThrownBy(() -> new KafkaBetPublisher(template, mapper).publish(BET))
          .isInstanceOf(PublicationException.class)
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void synchronousBrokerFailureReturnsServiceUnavailableWithRetryGuidance() throws Exception {
    try (var producer = failingProducer()) {
      ProducerFactory<String, String> factory = () -> producer;
      var publisher = new KafkaBetPublisher(new KafkaTemplate<>(factory), mapper);
      var repository = mock(BetRepository.class);
      when(repository.find(BET.betId())).thenReturn(Optional.empty());
      var service = new BetService(repository, publisher, mock(PolicyRegistry.class));
      var mvc =
          MockMvcBuilders.standaloneSetup(new BetController(service))
              .setControllerAdvice(new ApiExceptionHandler())
              .build();
      mvc.perform(
              post("/api/v1/bets")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(BET)))
          .andExpect(status().isServiceUnavailable())
          .andExpect(header().string("Retry-After", "1"))
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("detail").value("Kafka unavailable; retry with the same bet ID"));
    }
  }

  private MockProducer<String, String> failingProducer() {
    return new MockProducer<>(true, new StringSerializer(), new StringSerializer()) {
      @Override
      public synchronized Future<RecordMetadata> send(
          ProducerRecord<String, String> record, Callback callback) {
        var failure = new TimeoutException("Topic metadata unavailable");
        callback.onCompletion(null, failure);
        return CompletableFuture.failedFuture(failure);
      }
    };
  }

  @SuppressWarnings("unchecked")
  private KafkaTemplate<String, String> template() {
    return mock(KafkaTemplate.class);
  }
}
