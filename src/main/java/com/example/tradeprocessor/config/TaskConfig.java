package com.example.tradeprocessor.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskConfig {
  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    var ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(4);
    ex.setMaxPoolSize(16);
    ex.setQueueCapacity(1000);
    ex.setThreadNamePrefix("trade-processor-");
    ex.initialize();
    return ex;
  }
}
