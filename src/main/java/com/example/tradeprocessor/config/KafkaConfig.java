package com.example.tradeprocessor.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
    // Use Spring Boot's KafkaProperties so test-time DynamicPropertySource (Testcontainers)
    // overrides bootstrap servers correctly and we don't rely on a hard-coded localhost default.
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
    // Ensure sensible producer defaults in case properties are incomplete
    props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
    props.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, 5);
    props.putIfAbsent(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024);
    return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
