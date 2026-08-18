package com.bng.drivo.data.remote.dto;

import java.util.List;

public class WalletDto {
    public double balance;
    public List<WalletEntryDto> entries;

    public static class WalletEntryDto {
        public String type;
        public double amount;
        public String note;
        public String created_at;
    }
}
