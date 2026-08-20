package com.bng.drivo.data.model;

import java.util.List;

/** GET /driver/wallet — libro contable append-only, es la única fuente real de ganancias. */
public class Wallet {

    private final double balance;
    private final List<WalletEntry> entries;

    public Wallet(double balance, List<WalletEntry> entries) {
        this.balance = balance;
        this.entries = entries;
    }

    public double getBalance() {
        return balance;
    }

    public List<WalletEntry> getEntries() {
        return entries;
    }

    public static class WalletEntry {
        private final String type;
        private final double amount;
        private final String note;
        private final String createdAt;

        public WalletEntry(String type, double amount, String note, String createdAt) {
            this.type = type;
            this.amount = amount;
            this.note = note;
            this.createdAt = createdAt;
        }

        public String getType() {
            return type;
        }

        /** Negativo si es un cargo (comisión, ISR, IVA). */
        public double getAmount() {
            return amount;
        }

        public String getNote() {
            return note;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }
}
