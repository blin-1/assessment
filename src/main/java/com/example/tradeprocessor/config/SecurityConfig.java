package com.example.tradeprocessor.config;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // Configure security to use OAuth2 Resource Server (JWT/JWKS)
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
              auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                  .permitAll();

              // Only allow unauthenticated API access in dev profile
              if ("dev".equals(activeProfile)) {
                auth.requestMatchers("/api/**").permitAll();
              }

              auth.requestMatchers("/actuator/**").hasRole("SERVICE_ADMIN");
              auth.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

    return http.build();
  }

  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    // use 'roles' claim and prefix as 'ROLE_' so we can use hasRole("SERVICE_ADMIN")
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
    grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return converter;
  }

  /**
   * Provide a permissive JwtDecoder when no JwtDecoder is configured (e.g. in tests or dev). This
   * avoids failing application context startup when
   * spring.security.oauth2.resourceserver.jwt.jwk-set-uri is not provided. In production you should
   * configure a proper JwtDecoder via properties.
   */
  @Bean
  @ConditionalOnMissingBean
  public JwtDecoder jwtDecoder() {
    return token -> {
      Instant now = Instant.now();
      return Jwt.withTokenValue(token)
          .header("alg", "none")
          .issuedAt(now)
          .expiresAt(now.plusSeconds(3600))
          .claim("sub", "test-user")
          .claim("roles", List.of("SERVICE"))
          .build();
    };
  }
}
