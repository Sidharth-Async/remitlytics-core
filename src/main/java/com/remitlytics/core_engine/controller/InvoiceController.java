package com.remitlytics.core_engine.controller;

import com.remitlytics.core_engine.dto.CreateInvoiceRequest;
import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("/invoices")
    public ResponseEntity<Invoice> createInvoice(@Valid @RequestBody CreateInvoiceRequest request){
        Invoice createInvoice = invoiceService.createDraftInvoice(
                request.clientId(),
                request.amountCents(),
                request.dueDate()
        );
        return new ResponseEntity<>(createInvoice, HttpStatus.CREATED);
    }
}
