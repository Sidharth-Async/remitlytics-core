package com.remitlytics.core_engine.controller;


import com.remitlytics.core_engine.dto.WebhookEvent;
import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")

public class WebhookController {

    InvoiceService invoiceService;
    public WebhookController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/payments")
    public ResponseEntity<Void> handlePaymentWebhook(@RequestBody WebhookEvent event) {
        // Invoke service layer processing
        // Return a clean ResponseEntity.ok().build() (200 OK tells the gateway we got it)

        invoiceService.processPaymentWebhook(event, event.amountReceivedCents());
        return ResponseEntity.ok().build();
    }
}
