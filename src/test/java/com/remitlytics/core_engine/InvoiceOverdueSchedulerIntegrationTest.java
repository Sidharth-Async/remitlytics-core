package com.remitlytics.core_engine;

import com.remitlytics.core_engine.model.entities.*;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import com.remitlytics.core_engine.repository.*;
import com.remitlytics.core_engine.scheduler.InvoiceOverdueScheduler;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remitlytics_scheduler_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class InvoiceOverdueSchedulerIntegrationTest {

    @MockBean
    private Flyway flyway;

    @Autowired
    private InvoiceOverdueScheduler overdueScheduler;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceAuditLogRepository invoiceAuditLogRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Tenant testTenant;
    private Client testClient;

    @BeforeEach
    void setUp() {
        invoiceAuditLogRepository.deleteAll();
        invoiceRepository.deleteAll();
        clientRepository.deleteAll();
        organizationRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = new Tenant();
        testTenant.setCompanyName("Scheduler Test Tenant");
        testTenant = tenantRepository.save(testTenant);

        Organization org = new Organization();
        org.setName("Scheduler Test Org");
        org = organizationRepository.save(org);

        testClient = new Client();
        testClient.setName("Overdue Target Client");
        testClient.setEmail("overdue@client.com");
        testClient.setOrganization(org);
        testClient = clientRepository.save(testClient);
    }

    @Test
    @DisplayName("Scheduler should transition past-due SENT invoices to OVERDUE and leave current/DRAFT invoices untouched")
    void shouldSweepAndTransitionOnlyPastDueSentInvoices() {
        LocalDate today = LocalDate.now();

        // 1. Past due SENT -> Should transition to OVERDUE
        Invoice overdueSentInvoice = createInvoice(InvoiceStatus.SENT, today.minusDays(5));

        // 2. Future SENT -> Should stay SENT
        Invoice futureSentInvoice = createInvoice(InvoiceStatus.SENT, today.plusDays(5));

        // 3. Past due DRAFT -> Should stay DRAFT (not yet issued)
        Invoice pastDraftInvoice = createInvoice(InvoiceStatus.DRAFT, today.minusDays(3));

        // 4. Past due PAID -> Should stay PAID (terminal)
        Invoice pastPaidInvoice = createInvoice(InvoiceStatus.PAID, today.minusDays(10));

        // Run the sweep routine directly
        int processedCount = overdueScheduler.sweepOverdueInvoices();

        assertThat(processedCount).isEqualTo(1);

        // Verify status updates in database
        Invoice updatedOverdue = invoiceRepository.findById(overdueSentInvoice.getId()).orElseThrow();
        assertThat(updatedOverdue.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

        Invoice updatedFuture = invoiceRepository.findById(futureSentInvoice.getId()).orElseThrow();
        assertThat(updatedFuture.getStatus()).isEqualTo(InvoiceStatus.SENT);

        Invoice updatedDraft = invoiceRepository.findById(pastDraftInvoice.getId()).orElseThrow();
        assertThat(updatedDraft.getStatus()).isEqualTo(InvoiceStatus.DRAFT);

        Invoice updatedPaid = invoiceRepository.findById(pastPaidInvoice.getId()).orElseThrow();
        assertThat(updatedPaid.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // Verify audit log creation for the swept invoice
        List<InvoiceAuditLog> logs = invoiceAuditLogRepository.findByInvoiceIdOrderByCreatedAtAsc(overdueSentInvoice.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getPreviousStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(logs.getFirst().getNewStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(logs.getFirst().getReason()).contains("Automated scheduler sweep");
    }

    private Invoice createInvoice(InvoiceStatus status, LocalDate dueDate) {
        Invoice invoice = new Invoice();
        invoice.setTenant(testTenant);
        invoice.setClient(testClient);
        invoice.setAmountCents(25000L);
        invoice.setPlatformFeeCents(375L);
        invoice.setTaxCents(4567L);
        invoice.setTotalCents(29942L);
        invoice.setStatus(status);
        invoice.setDueDate(dueDate);
        return invoiceRepository.save(invoice);
    }
}