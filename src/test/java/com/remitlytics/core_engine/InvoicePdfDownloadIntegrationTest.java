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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remitlytics_pdf_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class InvoicePdfDownloadIntegrationTest {

    @MockBean
    private Flyway flyway;

    @Autowired
    private MockMvc mockMvc;

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

    private static final String API_KEY = "remit_live_pdf_test_key";
    private UUID testInvoiceId;

    @BeforeEach
    void setUp() throws Exception {
        invoiceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        clientRepository.deleteAll();
        organizationRepository.deleteAll();
        tenantRepository.deleteAll();

        // 1. Seed Tenant & Organization
        Tenant tenant = new Tenant();
        tenant.setCompanyName("PDF Acme Global");
        tenant = tenantRepository.save(tenant);

        Organization organization = new Organization();
        organization.setName("Acme Org");
        organization = organizationRepository.save(organization);

        // 2. Seed Client
        Client client = new Client();
        client.setName("Logistics Partner");
        client.setEmail("billing@partner.com");
        client.setOrganization(organization);
        client = clientRepository.save(client);

        // 3. Seed API Key
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(API_KEY.getBytes(StandardCharsets.UTF_8));
        String hashedKey = HexFormat.of().formatHex(hashBytes);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setKeyHash(hashedKey);
        apiKey.setActive(true);
        apiKeyRepository.save(apiKey);

        // 4. Seed Invoice
        Invoice invoice = new Invoice();
        invoice.setTenant(tenant);
        invoice.setClient(client);
        invoice.setAmountCents(100000L); // $1000.00
        invoice.setPlatformFeeCents(1500L); // $15.00
        invoice.setTaxCents(18270L); // $182.70
        invoice.setTotalCents(119770L); // $1197.70
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice = invoiceRepository.save(invoice);
        this.testInvoiceId = invoice.getId();
    }

    @Test
    @DisplayName("Downloading invoice PDF should return valid PDF binary with proper attachment headers")
    void shouldReturnValidPdfAttachment() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/invoices/{id}/pdf", testInvoiceId)
                        .header("X-API-KEY", API_KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"invoice-" + testInvoiceId + ".pdf\""))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();

        // Validate standard PDF magic byte signature: "%PDF"
        String magicHeader = new String(pdfBytes, 0, Math.min(pdfBytes.length, 4), StandardCharsets.US_ASCII);
        assertThat(magicHeader).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Downloading non-existent invoice PDF should return 404 NOT_FOUND")
    void shouldReturn404ForNonExistentInvoice() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/{id}/pdf", UUID.randomUUID())
                        .header("X-API-KEY", API_KEY))
                .andExpect(status().isNotFound());
    }
}