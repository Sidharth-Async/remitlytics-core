package com.remitlytics.core_engine.scheduler;

import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.model.entities.InvoiceAuditLog;
import com.remitlytics.core_engine.model.enums.InvoiceStatus;
import com.remitlytics.core_engine.repository.InvoiceAuditLogRepository;
import com.remitlytics.core_engine.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceOverdueScheduler {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceAuditLogRepository invoiceAuditLogRepository;

    private static final int BATCH_SIZE = 100;

    @Scheduled(cron = "${remitlytics.scheduler.overdue-cron:0 0 0 * * *}")
    public int sweepOverdueInvoices() {
        LocalDate today = LocalDate.now();
        log.info("Starting automated overdue invoice sweep for due dates prior to {}", today);

        int totalProcessed = 0;

        while (true) {
            // FIX: Always query page 0 because updated rows drop out of the 'SENT' filter
            Slice<Invoice> slice = invoiceRepository.findByStatusAndDueDateBefore(
                    InvoiceStatus.SENT,
                    today,
                    PageRequest.of(0, BATCH_SIZE)
            );

            if (slice.isEmpty()) {
                break;
            }

            for (Invoice invoice : slice.getContent()) {
                processSingleInvoice(invoice);
                totalProcessed++;
            }

            // Stop if there wasn't a full batch
            if (!slice.hasContent() || slice.getNumberOfElements() < BATCH_SIZE) {
                break;
            }
        }

        log.info("Completed overdue invoice sweep. Total transitioned to OVERDUE: {}", totalProcessed);
        return totalProcessed;
    }

    @Transactional
    public void processSingleInvoice(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.OVERDUE);
        invoiceRepository.save(invoice);

        InvoiceAuditLog auditLog = new InvoiceAuditLog();
        auditLog.setInvoice(invoice);
        auditLog.setPreviousStatus(InvoiceStatus.SENT);
        auditLog.setNewStatus(InvoiceStatus.OVERDUE);
        auditLog.setReason("Automated scheduler sweep: invoice past due date (" + invoice.getDueDate() + ")");
        invoiceAuditLogRepository.save(auditLog);
    }
}