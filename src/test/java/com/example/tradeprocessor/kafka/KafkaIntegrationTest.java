package com.example.tradeprocessor.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
public class KafkaIntegrationTest {

    @Container
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    public void endToEndProcessingPublishesToOutputTopic() throws Exception {
        String inputTopic = "trades-input";
        String outputTopic = "trades-output";

        // create producer
        var prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // Ensure topics exist and leader is elected before producing
        var adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        // convert Properties -> Map<String,Object> for AdminClient.create
        var adminConfig = new java.util.HashMap<String, Object>();
        for (String name : adminProps.stringPropertyNames()) {
            adminConfig.put(name, adminProps.getProperty(name));
        }
        try (AdminClient admin = AdminClient.create(adminConfig)) {
            var topics = List.of(new NewTopic(inputTopic, 1, (short)1), new NewTopic(outputTopic, 1, (short)1));
            try {
                admin.createTopics(topics).all().get();
            } catch (ExecutionException ee) {
                // topic may already exist; ignore
            }
        }

        try (var producer = new KafkaProducer<String, String>(prodProps)) {
            String payload = "{\"tradeId\":\"IT-1\",\"accountNumber\":\"1234567890\",\"accountName\":\"Test User\",\"amount\":100.0,\"currency\":\"USD\"}";
            producer.send(new ProducerRecord<>(inputTopic, null, payload)).get();
        }

        // create consumer to read output topic
        var consProps = new Properties();
        consProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        consProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (var consumer = new KafkaConsumer<String, String>(consProps)) {
            consumer.subscribe(List.of(outputTopic));

            ConsumerRecords<String, String> records = org.apache.kafka.clients.consumer.ConsumerRecords.empty();
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                records = consumer.poll(Duration.ofMillis(500));
                if (!records.isEmpty()) break;
            }

            assertThat(records).isNotNull();
            assertThat(records.isEmpty()).isFalse();
            var rec = records.iterator().next();
            assertThat(rec.value()).contains("ledgerReference");
        }
    }
}
