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

    /**
     * Runs daily at midnight (or configurable interval) to transition past-due SENT invoices to OVERDUE.
     */
    @Scheduled(cron = "${remitlytics.scheduler.overdue-cron:0 0 0 * * *}")
    @Transactional
    public int sweepOverdueInvoices() {
        LocalDate today = LocalDate.now();
        log.info("Starting automated overdue invoice sweep for due dates prior to {}", today);

        int totalProcessed = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            Slice<Invoice> slice = invoiceRepository.findByStatusAndDueDateBefore(
                    InvoiceStatus.SENT,
                    today,
                    PageRequest.of(pageNumber, BATCH_SIZE)
            );

            if (slice.isEmpty()) {
                break;
            }

            for (Invoice invoice : slice.getContent()) {
                invoice.setStatus(InvoiceStatus.OVERDUE);
                invoiceRepository.save(invoice);

                // Record audit log for automated transition
                InvoiceAuditLog auditLog = new InvoiceAuditLog();
                auditLog.setInvoice(invoice);
                auditLog.setPreviousStatus(InvoiceStatus.SENT);
                auditLog.setNewStatus(InvoiceStatus.OVERDUE);
                auditLog.setReason("Automated scheduler sweep: invoice past due date (" + invoice.getDueDate() + ")");
                invoiceAuditLogRepository.save(auditLog);

                totalProcessed++;
            }

            hasMore = slice.hasNext();
            pageNumber++;
        }

        log.info("Completed overdue invoice sweep. Total transitioned to OVERDUE: {}", totalProcessed);
        return totalProcessed;
    }
}