package com.example.tradeprocessor.service;

import com.example.tradeprocessor.model.InputTrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import com.example.tradeprocessor.model.CanonicalTrade;

import static org.assertj.core.api.Assertions.assertThat;

public class TradeProcessorServiceTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private TradeProcessorService service;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        // provide a real in-memory CacheManager so the service writes to the canonicalStore cache
        cacheManager = new ConcurrentMapCacheManager("canonicalStore");
        service = new TradeProcessorService(kafkaTemplate, mapper, cacheManager);
    }

    @Test
    void processSync_generatesCanonicalAndPublishes() {
        var in = new InputTrade();
        in.setTradeId("T-123");
        in.setAccountNumber("1234567890");
        in.setAccountName("John Doe");
        in.setAmount(1000.0);
        in.setCurrency("USD");

        String id = service.processSync(in);
        assertThat(id).isNotNull();

    // canonical should be present via the cache
    var canonical = service.getCanonical(id);
    assertThat(canonical).isNotNull();
    // also assert the cache contains the same object
    var cache = cacheManager.getCache("canonicalStore");
    assertThat(cache).isNotNull();
    var cached = cache.get(id, CanonicalTrade.class);
    assertThat(cached).isNotNull();
    assertThat(cached.getId()).isEqualTo(id);
        assertThat(canonical.getAccountNumber()).contains("*");
        assertThat(canonical.getAccountName()).contains(".");
    }
}
