package com.example.jackpot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.jackpot.domain.Bet;
import com.example.jackpot.messaging.KafkaConfiguration;
import com.example.jackpot.persistence.BetRepository;
import com.example.jackpot.service.BetService;
import com.example.jackpot.service.RewardDraw;
import java.math.BigDecimal;
import java.time.Duration;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "jackpot.messaging=kafka",
      "spring.kafka.admin.auto-create=true",
      "spring.datasource.url=jdbc:h2:mem:kafka-test;DB_CLOSE_DELAY=-1"
    })
@EmbeddedKafka(
    partitions = 3,
    topics = {"jackpot-bets", "jackpot-bets.DLT"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class KafkaIntegrationTest {
  @Autowired BetService service;
  @Autowired BetRepository bets;
  @Autowired KafkaTemplate<String, String> template;
  @Autowired EmbeddedKafkaBroker broker;
  @MockitoBean RewardDraw draw;

  @Test
  void realBrokerPublishesConsumesDeduplicatesAndQuarantinesPoisonRecords() throws Exception {
    when(draw.wins(any())).thenReturn(false);
    service.submit(new Bet("kafka-1", "user", "fixed", new BigDecimal("100.00")));
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(bets.find("kafka-1")).isPresent());
    assertThat(service.evaluate("kafka-1").contributionAmount()).isEqualByComparingTo("5");

    var consumerProperties = KafkaTestUtils.consumerProps("dlt-test", "false", broker);
    try (var consumer =
        new DefaultKafkaConsumerFactory<>(
                consumerProperties, new StringDeserializer(), new StringDeserializer())
            .createConsumer()) {
      broker.consumeFromAnEmbeddedTopic(consumer, KafkaConfiguration.DEAD_LETTER_TOPIC);
      template
          .send(
              KafkaConfiguration.BETS_TOPIC,
              "fixed",
              """
                    {"betId":"kafka-1","userId":"user","jackpotId":"fixed","amount":100}
                    """)
          .get();
      template.send(KafkaConfiguration.BETS_TOPIC, "fixed", "{malformed").get();
      var record =
          KafkaTestUtils.getSingleRecord(
              consumer, KafkaConfiguration.DEAD_LETTER_TOPIC, Duration.ofSeconds(20));
      assertThat(record.value()).isEqualTo("{malformed");
      // Same key/partition means the duplicate was consumed before the poison record.
      assertThat(service.evaluate("kafka-1").poolAfterContribution()).isEqualByComparingTo("1005");
    }
  }
}
