package com.example.tradeprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "InputTrade", description = "Incoming trade payload")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InputTrade {
    @NotBlank(message = "tradeId is required")
    @Schema(description = "Client trade identifier", example = "T-123")
    private String tradeId;

    @NotBlank(message = "accountNumber is required")
    @Schema(description = "Account number (sensitive - will be masked in logs)", example = "****5678")
    private String accountNumber;

    @NotBlank(message = "accountName is required")
    @Schema(description = "Account holder name", example = "Alice B")
    private String accountName;

    @Positive(message = "amount must be greater than 0")
    @Schema(description = "Trade amount", example = "1000.00")
    private double amount;

    @NotBlank(message = "currency is required")
    @Schema(description = "Currency code (ISO 4217)", example = "USD")
    private String currency;

    // optional - if missing we will set server time
    @Schema(description = "Trade timestamp (ISO-8601)")
    private Instant tradeDate;
    @Schema(description = "Security identifier", example = "AAPL")
    private String securityId;
    @Schema(description = "Trade type", example = "BUY")
    private String tradeType;
}
