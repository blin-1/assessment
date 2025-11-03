package com.example.tradeprocessor.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingTrade {
    private String ledgerReference;
    private String obfuscatedAccount;
    private double amount;
    private String currency;
    private Instant bookingDate;
}
