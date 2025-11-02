package com.example.tradeprocessor.service;

import com.example.tradeprocessor.model.InputTrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class TradeProcessorServiceTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private TradeProcessorService service;

    @BeforeEach
    void setUp() {
    kafkaTemplate = Mockito.mock(KafkaTemplate.class);
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    mapper.findAndRegisterModules();
    service = new TradeProcessorService(kafkaTemplate, mapper);
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

        var canonical = service.getCanonical(id);
        assertThat(canonical).isNotNull();
        assertThat(canonical.getAccountNumber()).contains("*");
        assertThat(canonical.getAccountName()).contains(".");
    }
}
