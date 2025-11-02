package com.example.tradeprocessor.service;

import com.example.tradeprocessor.model.AccountingTrade;
import com.example.tradeprocessor.model.CanonicalTrade;
import com.example.tradeprocessor.model.InputTrade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TradeProcessorService {
    private static final Logger log = LoggerFactory.getLogger(TradeProcessorService.class);

    private final Map<String, CanonicalTrade> store = new ConcurrentHashMap<>();
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.output-topic:trades-output}")
    private String outputTopic;

    public TradeProcessorService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Synchronous processing entrypoint used by controller when caller wants immediate response.
     */
    public String processSync(InputTrade input) {
        var canonical = toCanonical(input);
        store.put(canonical.getId(), canonical);
        applyTransformations(canonical);
        var accounting = toAccounting(canonical);
        String payload = toJson(accounting);
        kafkaTemplate.send(outputTopic, canonical.getId(), payload);
        log.info("Processed trade {} -> published to {}", canonical.getId(), outputTopic);
        return canonical.getId();
    }

    /**
     * Async processing used for file uploads / background work.
     */
    @Async("taskExecutor")
    public void processAsync(InputTrade input) {
        try {
            processSync(input);
        } catch (Exception e) {
            log.error("Async processing failed for trade {}", input == null ? "<null>" : input.getTradeId(), e);
        }
    }

    public CanonicalTrade getCanonical(String id) {
        return store.get(id);
    }

    public List<String> processBatch(List<InputTrade> inputs) {
        var ids = new ArrayList<String>();
        for (var t : inputs) {
            ids.add(processSync(t));
        }
        return ids;
    }

    private CanonicalTrade toCanonical(InputTrade in) {
        var c = new CanonicalTrade();
        c.setTradeId(in.getTradeId());
        c.setAccountNumber(in.getAccountNumber());
        c.setAccountName(in.getAccountName());
        c.setAmount(in.getAmount());
        c.setCurrency(in.getCurrency());
        c.setTradeDate(in.getTradeDate() == null ? Instant.now() : in.getTradeDate());
        return c;
    }

    private void applyTransformations(CanonicalTrade c) {
        // obfuscate account number
        var acc = c.getAccountNumber();
        if (acc != null && acc.length() > 4) {
            c.setAccountNumber(maskAccount(acc));
        }
        // redact account name (keep initials)
        var name = c.getAccountName();
        if (name != null && !name.isBlank()) {
            c.setAccountName(redactName(name));
        }
    }

    private AccountingTrade toAccounting(CanonicalTrade c) {
        var a = new AccountingTrade();
        a.setLedgerReference(c.getId());
        a.setObfuscatedAccount(c.getAccountNumber());
        a.setAmount(c.getAmount());
        a.setCurrency(c.getCurrency());
        a.setBookingDate(c.getTradeDate());
        return a;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload", e);
            throw new RuntimeException(e);
        }
    }

    private String maskAccount(String a) {
        int keep = 4;
        int len = a.length();
        if (len <= keep) return a;
        var sb = new StringBuilder();
        for (int i = 0; i < len - keep; i++) sb.append('*');
        sb.append(a.substring(len - keep));
        return sb.toString();
    }

    private String redactName(String name) {
        // keep initials and replace rest with *
        var parts = name.trim().split("\\s+");
        var sb = new StringBuilder();
        for (var p : parts) {
            if (p.length() > 0) {
                sb.append(p.charAt(0)).append(".");
            }
        }
        return sb.toString();
    }
}
