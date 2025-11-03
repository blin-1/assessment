package com.example.tradeprocessor.service;

import com.example.tradeprocessor.model.AccountingTrade;
import com.example.tradeprocessor.model.CanonicalTrade;
import com.example.tradeprocessor.model.InputTrade;
import com.example.tradeprocessor.util.InputSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TradeProcessorService {
  // Use Spring Cache abstraction to store canonical records in-memory for auditing/retry.
  // The cache name used is "canonicalStore" and defaults to a ConcurrentMap-backed cache
  // when Spring Cache is enabled and no other CacheManager is configured.
  private final CacheManager cacheManager;

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Value("${app.output-topic:trades-output}")
  private String outputTopic;

  @Value("${app.instructions-outbound:instructions.outbound}")
  private String instructionsOutboundTopic;

  /**
   * When {@code true} the service will block on kafkaTemplate.send() to make integration tests
   * deterministic. This flag is configured via application-test.properties for the "test" Spring
   * profile (app.sync-kafka-send=true).
   */
  @Value("${app.sync-kafka-send:false}")
  private boolean syncKafkaSend;

  public TradeProcessorService(
      KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper,
      CacheManager cacheManager) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.cacheManager = cacheManager;
  }

  /** Synchronous processing entrypoint used by controller when caller wants immediate response. */
  public String processSync(InputTrade input) {
    var canonical = toCanonical(input);
    // store canonical using Spring Cache
    var cache = getCanonicalCache();
    if (cache != null) {
      cache.put(canonical.getId(), canonical);
    }
    applyTransformations(canonical);
    var accounting = toAccounting(canonical);
    String payload = toJson(accounting);
    var futureOut = kafkaTemplate.send(outputTopic, canonical.getId(), payload);
    if (futureOut == null) {
      log.warn(
          "KafkaTemplate.send returned null for trade {} to {} - skipping publish callbacks",
          canonical.getId(),
          outputTopic);
    }

    // Build instructions payload JSON structure:
    // {
    //   "platform_id": "<raw account number>",
    //   "trade": { "account": "<obfuscated>", "security": "<SEC>", "type": "B|S", "amount": <num>,
    // "timestamp": "ISO_INSTANT" }
    // }
    var instrMap = new LinkedHashMap<String, Object>();
    var tradeMap = new LinkedHashMap<String, Object>();
    tradeMap.put("account", canonical.getAccountNumber());
    tradeMap.put("security", canonical.getSecurityId());
    tradeMap.put("type", canonical.getTradeType());
    tradeMap.put("amount", canonical.getAmount());
    tradeMap.put(
        "timestamp", canonical.getTradeDate() == null ? null : canonical.getTradeDate().toString());
    instrMap.put("platform_id", canonical.getRawAccountNumber());
    instrMap.put("trade", tradeMap);

    String instrPayload = toJson(instrMap);

    var futureInstr =
        kafkaTemplate.send(instructionsOutboundTopic, canonical.getId(), instrPayload);
    if (futureInstr == null) {
      log.warn(
          "KafkaTemplate.send returned null for trade {} to {} - skipping publish callbacks",
          canonical.getId(),
          instructionsOutboundTopic);
    }

    if (syncKafkaSend) {
      // In test mode block briefly to ensure the records are actually sent (makes tests
      // deterministic)
      try {
        if (futureOut != null) {
          var result = futureOut.get(10, TimeUnit.SECONDS);
          var meta = result.getRecordMetadata();
          log.info(
              "Processed trade {} -> published to {} (partition={}, offset={})",
              canonical.getId(),
              outputTopic,
              meta.partition(),
              meta.offset());
        }
        if (futureInstr != null) {
          var result2 = futureInstr.get(10, TimeUnit.SECONDS);
          var meta2 = result2.getRecordMetadata();
          log.info(
              "Processed trade {} -> published to {} (partition={}, offset={})",
              canonical.getId(),
              instructionsOutboundTopic,
              meta2.partition(),
              meta2.offset());
        }
      } catch (Exception e) {
        log.error(
            "Failed to publish trade {} to topic(s) {} or {}",
            canonical.getId(),
            outputTopic,
            instructionsOutboundTopic,
            e);
        // rethrow to make failures visible in tests
        throw new RuntimeException(e);
      }
    } else {
      // production: don't wait on the send futures (fire-and-forget)
      if (futureOut != null) {
        futureOut.whenComplete(
            (rec, ex) -> {
              if (ex != null) {
                log.error(
                    "Async publish failed for trade {} to topic {}",
                    canonical.getId(),
                    outputTopic,
                    ex);
              } else {
                try {
                  var meta = rec.getRecordMetadata();
                  log.info(
                      "Processed trade {} -> published to {} (partition={}, offset={}) (async)",
                      canonical.getId(),
                      outputTopic,
                      meta.partition(),
                      meta.offset());
                } catch (Exception e) {
                  log.info(
                      "Processed trade {} -> published to {} (async, metadata unavailable)",
                      canonical.getId(),
                      outputTopic);
                }
              }
            });
      }
      if (futureInstr != null) {
        futureInstr.whenComplete(
            (rec, ex) -> {
              if (ex != null) {
                log.error(
                    "Async publish failed for trade {} to topic {}",
                    canonical.getId(),
                    instructionsOutboundTopic,
                    ex);
              } else {
                try {
                  var meta = rec.getRecordMetadata();
                  log.info(
                      "Processed trade {} -> published to {} (partition={}, offset={}) (async)",
                      canonical.getId(),
                      instructionsOutboundTopic,
                      meta.partition(),
                      meta.offset());
                } catch (Exception e) {
                  log.info(
                      "Processed trade {} -> published to {} (async, metadata unavailable)",
                      canonical.getId(),
                      instructionsOutboundTopic);
                }
              }
            });
      }
    }
    return canonical.getId();
  }

  /** Async processing used for file uploads / background work. */
  @Async("taskExecutor")
  public void processAsync(InputTrade input) {
    try {
      processSync(input);
    } catch (Exception e) {
      log.error(
          "Async processing failed for trade {}", input == null ? "<null>" : input.getTradeId(), e);
    }
  }

  public CanonicalTrade getCanonical(String id) {
    var cache = getCanonicalCache();
    if (cache == null) return null;
    return cache.get(id, CanonicalTrade.class);
  }

  public List<String> processBatch(List<InputTrade> inputs) {
    var ids = new ArrayList<String>();
    for (var t : inputs) {
      ids.add(processSync(t));
    }
    return ids;
  }

  private Cache getCanonicalCache() {
    if (cacheManager == null) return null;
    return cacheManager.getCache("canonicalStore");
  }

  private CanonicalTrade toCanonical(InputTrade in) {
    var c = new CanonicalTrade();
    c.setTradeId(in.getTradeId());
    // preserve raw account number for platform_id usage, but accountNumber will be
    // obfuscated by applyTransformations
    c.setRawAccountNumber(in.getAccountNumber());
    c.setAccountNumber(in.getAccountNumber());
    c.setAccountName(in.getAccountName());
    c.setAmount(in.getAmount());
    c.setCurrency(in.getCurrency());
    c.setSecurityId(in.getSecurityId());
    c.setTradeType(in.getTradeType());
    c.setTradeDate(in.getTradeDate() == null ? Instant.now() : in.getTradeDate());
    return c;
  }

  private void applyTransformations(CanonicalTrade c) {
    // obfuscate account number using centralized sanitizer
    var acc = c.getAccountNumber();
    if (acc != null && acc.length() > 0) {
      c.setAccountNumber(InputSanitizer.maskAccount(acc));
    }
    // redact account name (keep initials) using centralized sanitizer
    var name = c.getAccountName();
    if (name != null && !name.isBlank()) {
      c.setAccountName(InputSanitizer.redactName(name));
    }
    // normalize security id: uppercase and validate
    var sec = c.getSecurityId();
    if (sec != null && !sec.isBlank()) {
      var up = sec.trim().toUpperCase();
      if (isValidSecurityId(up)) {
        c.setSecurityId(up);
      } else {
        log.warn("Invalid security id '{}' for trade {} - clearing value", sec, c.getId());
        c.setSecurityId(null);
      }
    }
    // normalize trade type to standard codes (B/S)
    var tt = c.getTradeType();
    if (tt != null && !tt.isBlank()) {
      var norm = normalizeTradeType(tt);
      if (norm != null) {
        c.setTradeType(norm);
      } else {
        log.warn("Unrecognized trade type '{}' for trade {} - clearing value", tt, c.getId());
        c.setTradeType(null);
      }
    }
  }

  private boolean isValidSecurityId(String s) {
    // Assumption: valid security id is 4-12 characters, uppercase alphanumeric or hyphen.
    // This is intentionally permissive; adjust regex for stricter formats (ISIN/CUSIP) if required.
    return s != null && s.matches("[A-Z0-9-]{4,12}");
  }

  private String normalizeTradeType(String t) {
    if (t == null) return null;
    var v = t.trim().toLowerCase();
    if (v.startsWith("b")) return "B";
    if (v.startsWith("s")) return "S";
    // accept full words like 'buy'/'sell', also single-letter codes
    switch (v) {
      case "buy":
      case "b":
        return "B";
      case "sell":
      case "s":
        return "S";
      default:
        return null;
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

  // Masking and redaction delegated to InputSanitizer for a single source of truth
}
