package com.example.tradeprocessor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test to ensure OpenAPI JSON is exposed by springdoc at /v3/api-docs.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_areAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(jwt().authorities()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").exists());
    }
}
