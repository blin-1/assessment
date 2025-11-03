package com.example.tradeprocessor.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
