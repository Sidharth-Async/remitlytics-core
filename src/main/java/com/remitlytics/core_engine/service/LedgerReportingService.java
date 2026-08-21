package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.AccountBalanceSummary;
import com.remitlytics.core_engine.dto.TrialBalanceResponse;
import com.remitlytics.core_engine.model.entities.LedgerAccount;
import com.remitlytics.core_engine.model.entities.LedgerEntry;
import com.remitlytics.core_engine.model.enums.EntryDirection;
import com.remitlytics.core_engine.repository.ApiKeyRepository;
import com.remitlytics.core_engine.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerReportingService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final ApiKeyRepository apiKeyRepository;

    @Transactional(readOnly = true)
    public TrialBalanceResponse generateTrialBalance(String rawApiKey) {
        UUID tenantId = resolveTenantIdFromApiKey(rawApiKey);
        return generateTrialBalanceForTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse generateTrialBalanceForTenant(UUID tenantId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findAllByTenantIdWithAccount(tenantId);

        // Group entries by account
        Map<LedgerAccount, List<LedgerEntry>> entriesByAccount = entries.stream()
                .collect(Collectors.groupingBy(LedgerEntry::getAccount));

        List<AccountBalanceSummary> accountSummaries = new ArrayList<>();
        long grandTotalDebit = 0L;
        long grandTotalCredit = 0L;

        for (Map.Entry<LedgerAccount, List<LedgerEntry>> mapEntry : entriesByAccount.entrySet()) {
            LedgerAccount account = mapEntry.getKey();
            List<LedgerEntry> accountEntries = mapEntry.getValue();

            long totalDebit = accountEntries.stream()
                    .filter(e -> e.getDirection() == EntryDirection.DEBIT)
                    .mapToLong(LedgerEntry::getAmountCents)
                    .sum();

            long totalCredit = accountEntries.stream()
                    .filter(e -> e.getDirection() == EntryDirection.CREDIT)
                    .mapToLong(LedgerEntry::getAmountCents)
                    .sum();

            long netBalance = totalDebit - totalCredit;

            grandTotalDebit += totalDebit;
            grandTotalCredit += totalCredit;

            accountSummaries.add(new AccountBalanceSummary(
                    account.getName(),
                    account.getType(),
                    totalDebit,
                    totalCredit,
                    netBalance
            ));
        }

        boolean isBalanced = (grandTotalDebit == grandTotalCredit);

        return new TrialBalanceResponse(
                accountSummaries,
                grandTotalDebit,
                grandTotalCredit,
                isBalanced
        );
    }

    private UUID resolveTenantIdFromApiKey(String rawApiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawApiKey.getBytes(StandardCharsets.UTF_8));
            String keyHash = HexFormat.of().formatHex(hash);

            return apiKeyRepository.findByKeyHashAndActiveTrue(keyHash)
                    .map(key -> key.getTenant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive API key"));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}