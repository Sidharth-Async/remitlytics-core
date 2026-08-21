package com.remitlytics.core_engine;

import com.remitlytics.core_engine.model.entities.*;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import com.remitlytics.core_engine.repository.*;
import com.remitlytics.core_engine.service.InvoiceService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remitlytics_email_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.mail.host=localhost",
        "spring.mail.port=25"
})
class InvoiceEmailNotificationIntegrationTest {

    @MockBean
    private Flyway flyway;

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Invoice draftInvoice;
    private Client testClient;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        clientRepository.deleteAll();
        organizationRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = new Tenant();
        tenant.setCompanyName("Mail Tenant Ltd");
        tenant = tenantRepository.save(tenant);

        Organization org = new Organization();
        org.setName("Mail Org");
        org = organizationRepository.save(org);

        testClient = new Client();
        testClient.setName("Acme Client");
        testClient.setEmail("finance@acme.com");
        testClient.setOrganization(org);
        testClient = clientRepository.save(testClient);

        Invoice invoice = new Invoice();
        invoice.setTenant(tenant);
        invoice.setClient(testClient);
        invoice.setAmountCents(100000L);
        invoice.setPlatformFeeCents(1500L);
        invoice.setTaxCents(18270L);
        invoice.setTotalCents(119770L);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setDueDate(LocalDate.now().plusDays(30));
        draftInvoice = invoiceRepository.save(invoice);

        // Provide a real MimeMessage instance for the mock mailSender
        Session mailSession = Session.getInstance(new Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(mailSession));
    }

    @Test
    @DisplayName("Transitioning invoice from DRAFT to SENT must trigger async email dispatch with PDF")
    void shouldSendEmailWhenInvoiceTransitionedToSent() {
        // Trigger status change to SENT
        invoiceService.updateInvoiceStatus(draftInvoice.getId(), InvoiceStatus.SENT, "Ready for client billing");

        // Verify async email sending using Awaitility
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender, atLeastOnce()).send(messageCaptor.capture());
            assertThat(messageCaptor.getValue()).isNotNull();
        });
    }
}