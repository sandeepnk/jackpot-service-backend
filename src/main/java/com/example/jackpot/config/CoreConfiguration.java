package com.example.jackpot.config;

import com.example.jackpot.service.RewardDraw;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  RewardDraw rewardDraw() {
    SecureRandom random = new SecureRandom();
    return chance ->
        chance.signum() > 0
            && (chance.compareTo(BigDecimal.valueOf(100)) >= 0
                || BigDecimal.valueOf(random.nextDouble()).movePointRight(2).compareTo(chance) < 0);
  }
}
