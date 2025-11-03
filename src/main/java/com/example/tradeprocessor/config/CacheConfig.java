package com.example.tradeprocessor.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit cache configuration.
 *
 * <p>Registers a simple in-memory ConcurrentMapCacheManager with an explicit cache name for
 * canonical records used by the application.
 */
@Configuration
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    // Register the 'canonicalStore' cache explicitly so it is always available in tests and runtime
    return new ConcurrentMapCacheManager("canonicalStore");
  }
}
