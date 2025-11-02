package com.example.tradeprocessor.service;

import com.example.tradeprocessor.model.CanonicalTrade;
import com.example.tradeprocessor.model.InputTrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.support.SendResult;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
public class TradeProcessorServiceIT {

    @Autowired
    private TradeProcessorService service;

    @Autowired
    private CacheManager cacheManager;

    // Prevent any real Kafka calls during integration test
    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void processSync_writesCanonicalToCache() throws Exception {
        var in = new InputTrade();
        in.setTradeId("INT-1");
        in.setAccountNumber("5555444433332222");
        in.setAccountName("Alice Example");
        in.setAmount(2500.0);
        in.setCurrency("USD");

        @SuppressWarnings("unchecked")
        SendResult<String, String> mockResult = (SendResult<String, String>) Mockito.mock(SendResult.class);
        var future = CompletableFuture.<SendResult<String, String>>completedFuture(mockResult);
        Mockito.when(kafkaTemplate.send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(future);

        String id = service.processSync(in);
        assertThat(id).isNotNull();

        // ensure cache exists
        Cache cache = cacheManager.getCache("canonicalStore");
        assertThat(cache).isNotNull();

        CanonicalTrade cached = cache.get(id, CanonicalTrade.class);
        assertThat(cached).isNotNull();
        assertThat(cached.getId()).isEqualTo(id);
        assertThat(cached.getAccountNumber()).contains("*");

        // capture and verify kafka send for instructions outbound payload
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(kafkaTemplate, Mockito.times(2)).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        List<String> topics = topicCaptor.getAllValues();
        List<String> payloads = payloadCaptor.getAllValues();
        int instrIndex = topics.indexOf("instructions.outbound");
        assertThat(instrIndex).isGreaterThanOrEqualTo(0);
        String instrPayload = payloads.get(instrIndex);

        var mapper = new ObjectMapper();
        @SuppressWarnings("unchecked") Map<String, Object> instrMap = mapper.readValue(instrPayload, Map.class);
        assertThat(instrMap.get("platform_id")).isEqualTo("5555444433332222");
        @SuppressWarnings("unchecked") Map<String, Object> tradeMap = (Map<String, Object>) instrMap.get("trade");
        assertThat(tradeMap.get("account").toString()).contains("*");
        assertThat(((Number)tradeMap.get("amount")).doubleValue()).isEqualTo(2500.0);
    }
}
