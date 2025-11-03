package com.example.tradeprocessor.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tradeprocessor.model.InputTrade;
import com.example.tradeprocessor.service.TradeProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TradeController.class)
public class TradeControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TradeProcessorService service;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void ingest_returnsBadRequestOnInvalid() throws Exception {
    var payload = "{" + "\"tradeId\":\"\",\"accountNumber\":\"\"" + "}";
    mockMvc
        .perform(
            post("/api/trades")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ingest_returnsCreatedOnValid() throws Exception {
    var in = new InputTrade();
    in.setTradeId("T-1");
    in.setAccountNumber("123456");
    in.setAccountName("Alice B");
    in.setAmount(10.0);
    in.setCurrency("USD");

    Mockito.when(service.processSync(Mockito.any())).thenReturn("canon-1");
    mockMvc
        .perform(
            post("/api/trades")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(in)))
        .andExpect(status().isCreated())
        .andExpect(content().string("canon-1"));
  }
}
