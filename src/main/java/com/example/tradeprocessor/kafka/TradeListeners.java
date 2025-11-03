package com.example.tradeprocessor.kafka;

import com.example.tradeprocessor.model.InputTrade;
import com.example.tradeprocessor.service.TradeProcessorService;
import com.example.tradeprocessor.util.InputSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TradeListeners {

    private final TradeProcessorService service;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public TradeListeners(TradeProcessorService service, ObjectMapper objectMapper, Validator validator) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @KafkaListener(topics = "${app.input-topic:trades-input}", groupId = "trade-processor-group")
    public void onMessage(ConsumerRecord<String, String> record) {
        var payload = record.value();
        String tradeId = null;
        try {
            // Try to extract tradeId for low-noise logging without printing the full payload
            var node = objectMapper.readTree(payload);
            tradeId = node.has("tradeId") ? node.get("tradeId").asText(null) : null;
        } catch (Exception ignored) {
            // intentionally ignore parsing errors here; we'll handle them below
        }

        log.info("Received message on topic {} partition {} tradeId={}", record.topic(), record.partition(), tradeId == null ? "<unknown>" : tradeId);

        try {
            var input = objectMapper.readValue(payload, InputTrade.class);

            // Validate input explicitly and reject if constraints fail
            if (validator != null) {
                Set<ConstraintViolation<InputTrade>> violations = validator.validate(input);
                if (!violations.isEmpty()) {
                    var sb = new StringBuilder();
                    for (var v : violations) {
                        sb.append(v.getPropertyPath()).append(": ").append(v.getMessage()).append("; ");
                    }
                    log.warn("Validation failed for incoming tradeId={} violations={}", input.getTradeId(), sb.toString());
                    return; // skip processing invalid input
                }
            }

            // Do not log raw payload or sensitive fields; if needed, log a sanitized copy
            var sanitized = InputSanitizer.sanitizedCopy(input);
            log.debug("Processing sanitized input: tradeId={} accountNumber={}", sanitized.getTradeId(), sanitized.getAccountNumber());

            // For integration tests we process synchronously to ensure the output is published
            // before the test consumer polls. In production this may be async.
            service.processSync(input);
        } catch (Exception e) {
            // Avoid logging full payload; include only identifiers when available
            log.error("Failed to process incoming message on topic {} partition {} tradeId={}", record.topic(), record.partition(), tradeId == null ? "<unknown>" : tradeId, e);
        }
    }

    @KafkaListener(topics = "instructions.inbound", groupId = "instruction-processor-group")
    public void onInstruction(ConsumerRecord<String, String> record) {
        var payload = record.value();
        String instructionId = null;
        try {
            var node = objectMapper.readTree(payload);
            instructionId = node.has("id") ? node.get("id").asText(null) : null;
            if (node.has("trade")) {
                var tradeNode = node.get("trade");
                var input = objectMapper.treeToValue(tradeNode, InputTrade.class);

                if (validator != null) {
                    var violations = validator.validate(input);
                    if (!violations.isEmpty()) {
                        var sb = new StringBuilder();
                        for (var v : violations) {
                            sb.append(v.getPropertyPath()).append(": ").append(v.getMessage()).append("; ");
                        }
                        log.warn("Validation failed for instruction id={} tradeId={} violations={}", instructionId, input.getTradeId(), sb.toString());
                        return;
                    }
                }

                // Use sanitized logging only
                var sanitized = InputSanitizer.sanitizedCopy(input);
                log.debug("Instruction {} contains trade tradeId={} accountNumber={}", instructionId == null ? "<unknown>" : instructionId, sanitized.getTradeId(), sanitized.getAccountNumber());

                // prefer async processing for instructions unless tests enable sync behavior
                service.processAsync(input);
            } else {
                log.info("Instruction received with no trade payload id={}", instructionId == null ? "<unknown>" : instructionId);
            }
        } catch (Exception e) {
            log.error("Failed to handle instruction payload on topic {} partition {} instructionId={}", record.topic(), record.partition(), instructionId == null ? "<unknown>" : instructionId, e);
        }
    }
}
