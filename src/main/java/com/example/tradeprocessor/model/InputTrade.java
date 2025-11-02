package com.example.tradeprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InputTrade {
    @NotBlank(message = "tradeId is required")
    private String tradeId;

    @NotBlank(message = "accountNumber is required")
    private String accountNumber;

    @NotBlank(message = "accountName is required")
    private String accountName;

    @Positive(message = "amount must be greater than 0")
    private double amount;

    @NotBlank(message = "currency is required")
    private String currency;

    // optional - if missing we will set server time
    private Instant tradeDate;

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getTradeDate() { return tradeDate; }
    public void setTradeDate(Instant tradeDate) { this.tradeDate = tradeDate; }
}
