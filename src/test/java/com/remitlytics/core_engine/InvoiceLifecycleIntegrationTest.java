package com.remitlytics.core_engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remitlytics_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class InvoiceLifecycleIntegrationTest {


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Flyway flyway;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceAuditLogRepository invoiceAuditLogRepository;

    private static final String RAW_API_KEY = "remit_live_testkey123";
    private UUID testClientId;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeEach
    void setUp() throws Exception {
        invoiceAuditLogRepository.deleteAll();
        invoiceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        clientRepository.deleteAll();
        organizationRepository.deleteAll(); // Clean up organizations
        tenantRepository.deleteAll();

        // 1. Seed Tenant
        Tenant tenant = new Tenant();
        tenant.setCompanyName("Acme Corp");
        tenant = tenantRepository.save(tenant);

        // 2. Seed Organization (if Organization is a distinct entity linked to Tenant or standalone)
        Organization organization = new Organization();
        organization.setName("Acme Org");
        // If Organization has a tenant relation, set it: organization.setTenant(tenant);
        organization = organizationRepository.save(organization);

        // 3. Seed Client with Organization reference
        Client client = new Client();
        client.setName("John Doe Logistics");
        client.setEmail("john@logistics.com");
        client.setOrganization(organization); // <-- Fixes the NULL organization_id error!
        client = clientRepository.save(client);
        this.testClientId = client.getId();

        // 4. Seed SHA-256 Hashed API Key
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(RAW_API_KEY.getBytes(StandardCharsets.UTF_8));
        String hashedKey = HexFormat.of().formatHex(hashBytes);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setKeyHash(hashedKey);
        apiKey.setActive(true);
        apiKeyRepository.save(apiKey);
    }

    @Test
    @DisplayName("Verify end-to-end invoice lifecycle: DRAFT -> SENT -> PAID with audit log tracking")
    void shouldTrackAuditLogsAcrossCompleteInvoiceLifecycle() throws Exception {

        // 1. CREATE DRAFT INVOICE
        String createDraftPayload = String.format("""
            {
                "clientId": "%s",
                "amountCents": 50000,
                "dueDate": "%s"
            }
            """, testClientId, LocalDate.now().plusDays(10));

        MvcResult createResult = mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-KEY", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.amountCents").value(50000))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID invoiceId = UUID.fromString(createdJson.get("id").asText());

        // Verify creation audit log
        List<InvoiceAuditLog> logsAfterCreate = invoiceAuditLogRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
        assertThat(logsAfterCreate).hasSize(1);
        assertThat(logsAfterCreate.getFirst().getPreviousStatus()).isNull();
        assertThat(logsAfterCreate.getFirst().getNewStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(logsAfterCreate.getFirst().getReason()).contains("Draft");

        // 2. TRANSITION: DRAFT -> SENT
        String sendPayload = """
            {
                "status": "SENT",
                "reason": "Dispatched invoice via email"
            }
            """;

        mockMvc.perform(patch("/api/v1/invoices/{id}/status", invoiceId)
                        .header("X-API-KEY", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));

        // Verify second audit log
        List<InvoiceAuditLog> logsAfterSent = invoiceAuditLogRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
        assertThat(logsAfterSent).hasSize(2);
        assertThat(logsAfterSent.get(1).getPreviousStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(logsAfterSent.get(1).getNewStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(logsAfterSent.get(1).getReason()).isEqualTo("Dispatched invoice via email");

        // 3. TRANSITION: SENT -> PAID
        String payPayload = """
            {
                "status": "PAID",
                "reason": "Payment verified via bank transfer"
            }
            """;

        mockMvc.perform(patch("/api/v1/invoices/{id}/status", invoiceId)
                        .header("X-API-KEY", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payPayload))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // Verify final audit logs state
        List<InvoiceAuditLog> logsAfterPaid = invoiceAuditLogRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
        assertThat(logsAfterPaid).hasSize(3);
        assertThat(logsAfterPaid.get(2).getPreviousStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(logsAfterPaid.get(2).getNewStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(logsAfterPaid.get(2).getReason()).isEqualTo("Payment verified via bank transfer");
    }

    @Test
    @DisplayName("Should reject illegal state transitions (e.g. DRAFT -> PAID directly)")
    void shouldRejectIllegalStateTransition() throws Exception {

        String createDraftPayload = String.format("""
            {
                "clientId": "%s",
                "amountCents": 10000,
                "dueDate": "%s"
            }
            """, testClientId, LocalDate.now().plusDays(5));

        MvcResult createResult = mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-KEY", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftPayload))
                .andExpect(status().isCreated())
                .andReturn();

        UUID invoiceId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

        String illegalPayload = """
            {
                "status": "PAID",
                "reason": "Attempting direct jump to PAID"
            }
            """;

        mockMvc.perform(patch("/api/v1/invoices/{id}/status", invoiceId)
                        .header("X-API-KEY", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(illegalPayload))
                .andExpect(status().is4xxClientError());

        List<InvoiceAuditLog> logs = invoiceAuditLogRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getNewStatus()).isEqualTo(InvoiceStatus.DRAFT);
    }
}