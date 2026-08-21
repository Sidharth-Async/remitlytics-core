package com.remitlytics.core_engine;

import com.remitlytics.core_engine.model.entities.*;
import com.remitlytics.core_engine.model.enums.AccountType;
import com.remitlytics.core_engine.model.enums.EntryDirection;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import com.remitlytics.core_engine.repository.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remitlytics_ledger_report_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class LedgerReportingIntegrationTest {

    @MockBean
    private Flyway flyway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ClientRepository clientRepository;

    private static final String API_KEY = "remit_live_ledger_report_key";
    private Tenant testTenant;

    @BeforeEach
    void setUp() throws Exception {
        ledgerEntryRepository.deleteAll();
        ledgerTransactionRepository.deleteAll();
        ledgerAccountRepository.deleteAll();
        invoiceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        clientRepository.deleteAll();
        organizationRepository.deleteAll();
        tenantRepository.deleteAll();

        // 1. Seed Tenant
        testTenant = new Tenant();
        testTenant.setCompanyName("Reporting Tenant Inc");
        testTenant = tenantRepository.save(testTenant);

        // 2. Seed API Key
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(API_KEY.getBytes(StandardCharsets.UTF_8));
        String keyHash = HexFormat.of().formatHex(hash);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(testTenant);
        apiKey.setKeyHash(keyHash);
        apiKey.setActive(true);
        apiKeyRepository.save(apiKey);

        // 3. Seed Accounts
        LedgerAccount bank = new LedgerAccount();
        bank.setName("BANK_MAIN");
        bank.setType(AccountType.ASSET);
        bank.setTenant(testTenant);
        bank = ledgerAccountRepository.save(bank);

        LedgerAccount ar = new LedgerAccount();
        ar.setName("ACCOUNTS_RECEIVABLE");
        ar.setType(AccountType.ASSET);
        ar.setTenant(testTenant);
        ar = ledgerAccountRepository.save(ar);

        // 4. Seed Balanced Transaction
        LedgerTransaction tx = new LedgerTransaction();
        tx.setTenant(testTenant);
        tx.setIdempotencyKey("PAYMENT_SETTLEMENT_" + UUID.randomUUID());
        tx.setDescription("Invoice Settlement Test");
        tx = ledgerTransactionRepository.save(tx);

        // Debit Bank 50,000 cents ($500)
        LedgerEntry debitEntry = new LedgerEntry();
        debitEntry.setTransaction(tx);
        debitEntry.setAccount(bank);
        debitEntry.setDirection(EntryDirection.DEBIT);
        debitEntry.setAmountCents(50000L);
        ledgerEntryRepository.save(debitEntry);

        // Credit Accounts Receivable 50,000 cents ($500)
        LedgerEntry creditEntry = new LedgerEntry();
        creditEntry.setTransaction(tx);
        creditEntry.setAccount(ar);
        creditEntry.setDirection(EntryDirection.CREDIT);
        creditEntry.setAmountCents(50000L);
        ledgerEntryRepository.save(creditEntry);
    }

    @Test
    @DisplayName("Trial balance should return matching grand debits and credits with isBalanced = true")
    void shouldReturnBalancedTrialBalance() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/trial-balance")
                        .header("X-API-KEY", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDebitCents").value(50000))
                .andExpect(jsonPath("$.totalCreditCents").value(50000))
                .andExpect(jsonPath("$.isBalanced").value(true))
                .andExpect(jsonPath("$.accounts").isArray())
                .andExpect(jsonPath("$.accounts.length()").value(2));
    }
}