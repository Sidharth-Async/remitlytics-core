package com.remitlytics.core_engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitlytics.core_engine.model.entities.*;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import com.remitlytics.core_engine.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remitlytics_idempotency_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PaymentIdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private static final String RAW_API_KEY = "remit_live_idempotency_test_key";
    private UUID testInvoiceId;
    private Tenant testTenant;

    @BeforeEach
    void setUp() throws Exception {
        ledgerEntryRepository.deleteAll();
        ledgerTransactionRepository.deleteAll();
        invoiceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        clientRepository.deleteAll();
        organizationRepository.deleteAll();
        tenantRepository.deleteAll();

        // 1. Seed Tenant & Org
        testTenant = new Tenant();
        testTenant.setCompanyName("Idempotency Corp");
        testTenant = tenantRepository.save(testTenant);

        Organization organization = new Organization();
        organization.setName("Idempotency Org");
        organization = organizationRepository.save(organization);

        // 2. Seed Client
        Client client = new Client();
        client.setName("Payment Tester");
        client.setEmail("pay@test.com");
        client.setOrganization(organization);
        client = clientRepository.save(client);

        // 3. Seed API Key
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(RAW_API_KEY.getBytes(StandardCharsets.UTF_8));
        String hashedKey = HexFormat.of().formatHex(hashBytes);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(testTenant);
        apiKey.setKeyHash(hashedKey);
        apiKey.setActive(true);
        apiKeyRepository.save(apiKey);

        // 4. Seed an Invoice in SENT state
        Invoice invoice = new Invoice();
        invoice.setTenant(testTenant);
        invoice.setClient(client);
        invoice.setAmountCents(75000L);
        invoice.setPlatformFeeCents(1125L);
        invoice.setTaxCents(13702L);
        invoice.setTotalCents(89827L);
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setDueDate(LocalDate.now().plusDays(15));
        invoice = invoiceRepository.save(invoice);
        this.testInvoiceId = invoice.getId();
    }

    @Test
    @DisplayName("Duplicate payment submissions must execute ledger mutations once and return identical state")
    void shouldHandleDuplicatePaymentTransitionsIdempotently() throws Exception {
        String payPayload = """
            {
                "status": "PAID",
                "reason": "Settled via automated direct debit"
            }
            """;

        // FIRST REQUEST: Transitions status to PAID and writes ledger entries
        MvcResult firstResponse = mockMvc.perform(patch("/api/v1/invoices/{id}/status", testInvoiceId)
                        .header("X-API-KEY", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalCents").value(89827))
                .andReturn();

        // SECOND REQUEST: Identical duplicate request replayed
        MvcResult secondResponse = mockMvc.perform(patch("/api/v1/invoices/{id}/status", testInvoiceId)
                        .header("X-API-KEY", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andReturn();

        // 1. Assert response payloads are identical
        assertThat(firstResponse.getResponse().getContentAsString())
                .isEqualTo(secondResponse.getResponse().getContentAsString());

        // 2. Assert exactly one LedgerTransaction exists with the idempotency key
        String expectedIdempotencyKey = "PAYMENT_SETTLEMENT_" + testInvoiceId;
        List<LedgerTransaction> transactions = ledgerTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getIdempotencyKey()).isEqualTo(expectedIdempotencyKey);
        assertThat(transactions.getFirst().getInvoiceId()).isEqualTo(testInvoiceId);

        // 3. Assert exactly 2 balanced ledger entries exist (1 Debit Bank, 1 Credit AR)
        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertThat(entries).hasSize(2);

        long totalDebits = entries.stream()
                .filter(e -> "DEBIT".equalsIgnoreCase(e.getEntryType()))
                .mapToLong(LedgerEntry::getAmountCents)
                .sum();
        long totalCredits = entries.stream()
                .filter(e -> "CREDIT".equalsIgnoreCase(e.getEntryType()))
                .mapToLong(LedgerEntry::getAmountCents)
                .sum();

        assertThat(totalDebits).isEqualTo(89827L);
        assertThat(totalCredits).isEqualTo(89827L);
    }
}