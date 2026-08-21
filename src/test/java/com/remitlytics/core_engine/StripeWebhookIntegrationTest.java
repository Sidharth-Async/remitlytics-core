package com.remitlytics.core_engine;

import com.remitlytics.core_engine.model.entities.*;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remitlytics_stripe_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "remitlytics.stripe.webhook-secret=whsec_test_secret_123"
})
class StripeWebhookIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_123";

    @MockBean
    private Flyway flyway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Tenant testTenant;
    private Client testClient;
    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        ledgerTransactionRepository.deleteAll();
        invoiceRepository.deleteAll();
        clientRepository.deleteAll();
        organizationRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = new Tenant();
        testTenant.setCompanyName("Stripe Test Tenant");
        testTenant = tenantRepository.save(testTenant);

        Organization org = new Organization();
        org.setName("Stripe Test Org");
        org = organizationRepository.save(org);

        testClient = new Client();
        testClient.setName("Stripe Customer");
        testClient.setEmail("stripe@customer.com");
        testClient.setOrganization(org);
        testClient = clientRepository.save(testClient);

        Invoice invoice = new Invoice();
        invoice.setTenant(testTenant);
        invoice.setClient(testClient);
        invoice.setAmountCents(80000L);
        invoice.setPlatformFeeCents(1200L);
        invoice.setTaxCents(14616L);
        invoice.setTotalCents(95816L);
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setDueDate(LocalDate.now().plusDays(10));
        testInvoice = invoiceRepository.save(invoice);
    }

    @Test
    @DisplayName("Valid checkout.session.completed event should settle invoice to PAID and write ledger entries")
    void shouldProcessValidStripeCheckoutCompletedEvent() throws Exception {
        String payload = """
            {
              "id": "evt_test_123",
              "object": "event",
              "type": "checkout.session.completed",
              "data": {
                "object": {
                  "id": "cs_test_session_abc",
                  "object": "checkout.session",
                  "payment_intent": "pi_test_intent_xyz",
                  "client_reference_id": "%s",
                  "metadata": {
                    "invoice_id": "%s"
                  }
                }
              }
            }
            """.formatted(testInvoice.getId(), testInvoice.getId());

        long timestamp = Instant.now().getEpochSecond();
        String signature = computeStripeSignature(timestamp + "." + payload, WEBHOOK_SECRET);
        String sigHeader = "t=" + timestamp + ",v1=" + signature;

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", sigHeader)
                        .content(payload))
                .andExpect(status().isOk());

        // 1. Verify invoice transitioned to PAID
        Invoice updated = invoiceRepository.findById(testInvoice.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // 2. Verify double-entry ledger entries exist
        List<LedgerTransaction> transactions = ledgerTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getInvoiceId()).isEqualTo(testInvoice.getId());

        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertThat(entries).hasSize(2);
    }

    private String computeStripeSignature(String data, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    @DisplayName("Webhook with forged signature must be rejected with 400 Bad Request")
    void shouldRejectInvalidSignature() throws Exception {
        String payload = "{\"id\":\"evt_fake\",\"type\":\"checkout.session.completed\"}";
        String invalidSigHeader = "t=" + Instant.now().getEpochSecond() + ",v1=invalid_signature_hash";

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", invalidSigHeader)
                        .content(payload))
                .andExpect(status().isBadRequest());

        // Ensure invoice remains SENT
        Invoice untouched = invoiceRepository.findById(testInvoice.getId()).orElseThrow();
        assertThat(untouchableState(untouched)).isEqualTo(InvoiceStatus.SENT);
    }

    private InvoiceStatus untouchableState(Invoice invoice) {
        return invoice.getStatus();
    }
}