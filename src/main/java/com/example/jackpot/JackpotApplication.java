package com.example.jackpot;

import com.example.jackpot.config.JackpotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JackpotProperties.class)
public class JackpotApplication {
  public static void main(String[] args) {
    SpringApplication.run(JackpotApplication.class, args);
  }
}
