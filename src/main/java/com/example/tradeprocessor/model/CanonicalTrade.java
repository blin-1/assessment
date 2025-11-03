package com.example.tradeprocessor.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CanonicalTrade {
    private String id = UUID.randomUUID().toString();
    private String tradeId;
    private String accountNumber;
    private String rawAccountNumber;
    private String accountName;
    private double amount;
    private String currency;
    private Instant tradeDate;
    private String securityId;
    private String tradeType;
}
