package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.event.InvoiceSentEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceEmailNotificationListener {

    private final JavaMailSender mailSender;
    private final PdfExportService pdfExportService;

    @Value("${spring.mail.username:billing@remitlytics.com}")
    private String senderEmail;

    @Async
    @EventListener
    public void onInvoiceSent(InvoiceSentEvent event) {
        log.info("Processing asynchronous invoice email notification for invoice: {}", event.invoice().id());

        try {
            byte[] pdfBytes = pdfExportService.generateInvoicePdf(event.invoice());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(event.recipientEmail());
            helper.setSubject("Tax Invoice #" + event.invoice().id() + " from Remitlytics");

            BigDecimal amount = BigDecimal.valueOf(event.invoice().totalCents())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            String htmlContent = """
                <p>Hello %s,</p>
                <p>Please find attached your invoice <strong>#%s</strong> for the total amount of <strong>$%s</strong>.</p>
                <p>Due Date: %s</p>
                <p>Thank you for your business.<br/>Remitlytics Core Engine</p>
                """.formatted(
                    event.recipientName(),
                    event.invoice().id(),
                    amount,
                    event.invoice().dueDate()
            );

            helper.setText(htmlContent, true);
            helper.addAttachment("invoice-" + event.invoice().id() + ".pdf", new ByteArrayResource(pdfBytes));

            mailSender.send(message);
            log.info("Successfully dispatched email with invoice PDF attachment to {}", event.recipientEmail());

        } catch (MessagingException e) {
            log.error("Failed to compose or send invoice email for invoice {}: {}", event.invoice().id(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during async invoice email dispatch", e);
        }
    }
}