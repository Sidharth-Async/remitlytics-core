package com.remitlytics.core_engine.scheduler;

import com.remitlytics.core_engine.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceOverdueScheduler {

    private final InvoiceService invoiceService;

    // Cron expression: Runs at midnight every day in production ("0 0 0 * * ?")
    // For testing right now, set fixedRate = 30000 (runs every 30 seconds)
    @Scheduled(fixedRate = 30000)
    public void sweepOverdueInvoices() {
        log.info("Starting automated overdue invoice sweep...");
        int processedCount = invoiceService.processOverdueInvoices();
        if (processedCount > 0) {
            log.info("Cron Sweep Complete: Successfully transitioned {} invoices to OVERDUE.", processedCount);
        }
    }
}