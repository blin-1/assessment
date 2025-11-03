package com.example.tradeprocessor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;

import com.example.tradeprocessor.model.InputTrade;
import com.example.tradeprocessor.service.TradeProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

public class TradeControllerCsvTest {

  private TradeProcessorService service;
  private ObjectMapper objectMapper;
  private Validator validator;
  private TradeController controller;

  @BeforeEach
  void setUp() {
    service = Mockito.mock(TradeProcessorService.class);
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    controller = new TradeController(service, objectMapper, validator);
  }

  @Test
  void uploadCsv_happyPath_processesAllRows() throws IOException {
    String csv =
        "tradeId,accountNumber,accountName,amount,currency,tradeDate\n"
            + "T-1,1234567890,John Doe,100.0,USD,2025-11-02T12:00:00Z\n"
            + "T-2,0987654321,Jane Smith,200.5,EUR,2025-11-02T13:00:00Z\n";

    var file =
        new MockMultipartFile(
            "file", "trades.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    ResponseEntity<Object> resp = controller.uploadFile(file);

    assertThat(resp.getStatusCodeValue()).isEqualTo(202);
    var body = (Map<String, Object>) resp.getBody();
    assertThat(body).isNotNull();
    var accepted = (List<String>) body.get("accepted");
    var errors = (Map<?, ?>) body.get("errors");
    assertThat(accepted).hasSize(2);
    assertThat(errors).isEmpty();

    Mockito.verify(service, times(2)).processAsync(Mockito.any(InputTrade.class));
  }

  @Test
  void uploadCsv_malformedRow_reportsErrorAndProcessesValid() throws IOException {
    String csv =
        "tradeId,accountNumber,accountName,amount,currency,tradeDate\n"
            + "T-1,1234567890,John Doe,100.0,USD,2025-11-02T12:00:00Z\n"
            + "T-2,0987654321,Jane Smith,NOT_A_NUMBER,EUR,2025-11-02T13:00:00Z\n";

    var file =
        new MockMultipartFile(
            "file", "trades.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    ResponseEntity<Object> resp = controller.uploadFile(file);

    assertThat(resp.getStatusCodeValue()).isEqualTo(202);
    var body = (Map<String, Object>) resp.getBody();
    assertThat(body).isNotNull();
    var accepted = (List<String>) body.get("accepted");
    var errors = (Map<Integer, List<String>>) body.get("errors");

    // one valid row processed, one parse error reported
    assertThat(accepted).hasSize(1);
    assertThat(errors).isNotEmpty();

    Mockito.verify(service, times(1)).processAsync(Mockito.any(InputTrade.class));
  }
}
