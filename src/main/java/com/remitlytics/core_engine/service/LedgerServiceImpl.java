package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.entities.LedgerAccount;
import com.remitlytics.core_engine.model.entities.LedgerEntry;
import com.remitlytics.core_engine.model.entities.LedgerTransaction;
import com.remitlytics.core_engine.repository.LedgerAccountRepository;
import com.remitlytics.core_engine.repository.LedgerEntryRepository;
import com.remitlytics.core_engine.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerServiceImpl implements LedgerService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    @Override
    @Transactional
    public LedgerTransaction recordInvoicePayment(Invoice invoice) {
        String idempotencyKey = "PAYMENT_SETTLEMENT_" + invoice.getId();

        Optional<LedgerTransaction> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            log.warn("Payment ledger transaction already recorded for invoice {}. Returning existing record.", invoice.getId());
            return existingTx.get();
        }

        UUID tenantId = invoice.getTenant().getId();

        LedgerAccount bankAccount = accountRepository.findByTenantIdAndAccountType(tenantId, "BANK")
                .orElseGet(() -> createDefaultAccount(tenantId, "BANK", "Operating Bank Account"));

        LedgerAccount arAccount = accountRepository.findByTenantIdAndAccountType(tenantId, "ACCOUNTS_RECEIVABLE")
                .orElseGet(() -> createDefaultAccount(tenantId, "ACCOUNTS_RECEIVABLE", "Accounts Receivable"));

        LedgerTransaction tx = LedgerTransaction.builder()
                .tenantId(tenantId)
                .invoiceId(invoice.getId())
                .idempotencyKey(idempotencyKey) // <-- Added idempotency key mapping
                .description("Payment settlement for Invoice ID: " + invoice.getId())
                .build();

        LedgerTransaction savedTx = transactionRepository.saveAndFlush(tx);

        Long amount = invoice.getTotalCents() != null ? invoice.getTotalCents() :
                (invoice.getAmountCents() != null ? invoice.getAmountCents() : 0L);

        LedgerEntry debitBank = LedgerEntry.builder()
                .transaction(savedTx)
                .accountId(bankAccount.getId())
                .amountCents(amount)
                .entryType("DEBIT")
                .build();

        LedgerEntry creditAR = LedgerEntry.builder()
                .transaction(savedTx)
                .accountId(arAccount.getId())
                .amountCents(amount)
                .entryType("CREDIT")
                .build();

        entryRepository.saveAndFlush(debitBank);
        entryRepository.saveAndFlush(creditAR);

        return savedTx;
    }

    private LedgerAccount createDefaultAccount(UUID tenantId, String accountType, String name) {
        LedgerAccount account = LedgerAccount.builder()
                .tenantId(tenantId)
                .accountType(accountType)
                .name(name)
                .build();
        return accountRepository.save(account);
    }
}