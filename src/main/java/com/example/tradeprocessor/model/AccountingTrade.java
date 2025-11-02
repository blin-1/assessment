package com.example.tradeprocessor.model;

import java.time.Instant;

public class AccountingTrade {
    private String ledgerReference;
    private String obfuscatedAccount;
    private double amount;
    private String currency;
    private Instant bookingDate;

    public String getLedgerReference() { return ledgerReference; }
    public void setLedgerReference(String ledgerReference) { this.ledgerReference = ledgerReference; }
    public String getObfuscatedAccount() { return obfuscatedAccount; }
    public void setObfuscatedAccount(String obfuscatedAccount) { this.obfuscatedAccount = obfuscatedAccount; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getBookingDate() { return bookingDate; }
    public void setBookingDate(Instant bookingDate) { this.bookingDate = bookingDate; }
}
