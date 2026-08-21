package com.remitlytics.core_engine.dto;

import java.util.List;

public record TrialBalanceResponse(
        List<AccountBalanceSummary> accounts,
        Long totalDebitCents,
        Long totalCreditCents,
        boolean isBalanced
) {}