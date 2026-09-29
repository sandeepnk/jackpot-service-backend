package com.example.jackpot.config;

import com.example.jackpot.persistence.JackpotRepository;
import com.example.jackpot.service.PolicyRegistry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Initializes the assignment's ephemeral pools before Kafka listeners accept records. */
@Component
@DependsOnDatabaseInitialization
public class JackpotInitializer implements InitializingBean {
  private final JackpotProperties properties;
  private final JackpotRepository repository;
  private final TransactionTemplate transactions;

  public JackpotInitializer(
      JackpotProperties properties,
      JackpotRepository repository,
      TransactionTemplate transactions,
      PolicyRegistry policies) {
    this.properties = properties;
    this.repository = repository;
    this.transactions = transactions;
    // Force policy validation before creating any pool.
    properties.definitions().forEach(definition -> policies.definition(definition.id()));
  }

  @Override
  public void afterPropertiesSet() {
    transactions.executeWithoutResult(
        status ->
            properties
                .definitions()
                .forEach(
                    definition ->
                        repository.initialize(definition.id(), definition.initialPool())));
  }
}
