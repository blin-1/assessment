package com.example.tradeprocessor.model;

import java.time.Instant;
import java.util.UUID;

public class CanonicalTrade {
    private String id = UUID.randomUUID().toString();
    private String tradeId;
    private String accountNumber;
    private String accountName;
    private double amount;
    private String currency;
    private Instant tradeDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
