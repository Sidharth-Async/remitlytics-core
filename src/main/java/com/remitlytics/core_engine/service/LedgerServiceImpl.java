package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.entities.LedgerAccount;
import com.remitlytics.core_engine.model.entities.LedgerEntry;
import com.remitlytics.core_engine.model.entities.LedgerTransaction;
import com.remitlytics.core_engine.model.entities.Tenant;
import com.remitlytics.core_engine.model.enums.AccountType;
import com.remitlytics.core_engine.model.enums.EntryDirection;
import com.remitlytics.core_engine.repository.LedgerAccountRepository;
import com.remitlytics.core_engine.repository.LedgerEntryRepository;
import com.remitlytics.core_engine.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerServiceImpl implements LedgerService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    @Override
    @Transactional
    public void recordInvoicePayment(Invoice invoice) {
        String idempotencyKey = "PAYMENT_SETTLEMENT_" + invoice.getId();

        // 1. Idempotency Check
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("Ledger transaction already processed for idempotencyKey: {}", idempotencyKey);
            return;
        }

        Tenant tenant = invoice.getTenant();
        LedgerAccount bankAccount = getOrCreateAccount(tenant, "BANK", AccountType.ASSET);
        LedgerAccount arAccount = getOrCreateAccount(tenant, "ACCOUNTS_RECEIVABLE", AccountType.ASSET);

        // 2. Create and Save Parent Transaction
        LedgerTransaction transaction = LedgerTransaction.builder()
                .tenant(tenant)
                .invoiceId(invoice.getId())
                .idempotencyKey(idempotencyKey)
                .description("Payment settlement for invoice: " + invoice.getId())
                .build();

        try {
            transaction = transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate transaction prevented by DB constraint: {}", idempotencyKey);
            return;
        }

        // 3. Balanced Double-Entry Postings (Debit Cash/Bank, Credit AR)
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(bankAccount)
                .direction(EntryDirection.DEBIT)
                .amountCents(invoice.getTotalCents())
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(arAccount)
                .direction(EntryDirection.CREDIT)
                .amountCents(invoice.getTotalCents())
                .build();

        entryRepository.save(debitEntry);
        entryRepository.save(creditEntry);

        log.info("Successfully recorded balanced double-entry ledger transaction: {}", transaction.getId());
    }

    private LedgerAccount getOrCreateAccount(Tenant tenant, String name, AccountType type) {
        return accountRepository.findByTenantIdAndName(tenant.getId(), name)
                .orElseGet(() -> accountRepository.save(
                        LedgerAccount.builder()
                                .tenant(tenant)
                                .name(name)
                                .type(type)
                                .build()
                ));
    }
}