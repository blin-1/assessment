package com.example.tradeprocessor.kafka;

import com.example.tradeprocessor.model.InputTrade;
import com.example.tradeprocessor.service.TradeProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TradeListeners {
    private static final Logger log = LoggerFactory.getLogger(TradeListeners.class);

    private final TradeProcessorService service;
    private final ObjectMapper objectMapper;

    public TradeListeners(TradeProcessorService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.input-topic:trades-input}", groupId = "trade-processor-group")
    public void onMessage(ConsumerRecord<String, String> record) {
        var payload = record.value();
        log.info("Received message on topic {} partition {}: {}", record.topic(), record.partition(), payload);
        try {
            var input = objectMapper.readValue(payload, InputTrade.class);
            // For integration tests we process synchronously to ensure the output is published
            // before the test consumer polls. In production this may be async.
            service.processSync(input);
        } catch (Exception e) {
            log.error("Failed to process incoming message: {}", payload, e);
        }
    }
}
