package com.example.tradeprocessor.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder ->
        builder.postConfigurer(
            (ObjectMapper mapper) -> {
              // Explicitly disable default typing to avoid polymorphic deserialization
              // which can lead to gadget-based deserialization attacks when enabled.
              mapper.setDefaultTyping(null);

              // Be explicit about unknown properties handling. Models may override
              // via annotations, but keep a safe default here.
              mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            });
  }
}
