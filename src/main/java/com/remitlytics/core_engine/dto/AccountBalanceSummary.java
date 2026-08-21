package com.remitlytics.core_engine.dto;

import com.remitlytics.core_engine.model.enums.AccountType;

public record AccountBalanceSummary(
        String accountName,
        AccountType accountType,
        Long totalDebitCents,
        Long totalCreditCents,
        Long netBalanceCents
) {
    public AccountBalanceSummary(String accountName, AccountType accountType, Long totalDebitCents, Long totalCreditCents) {
        this(
                accountName,
                accountType,
                totalDebitCents != null ? totalDebitCents : 0L,
                totalCreditCents != null ? totalCreditCents : 0L,
                (totalDebitCents != null ? totalDebitCents : 0L) - (totalCreditCents != null ? totalCreditCents : 0L)
        );
    }
}