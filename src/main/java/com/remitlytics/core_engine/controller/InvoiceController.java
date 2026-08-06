package com.remitlytics.core_engine.controller;

import com.remitlytics.core_engine.dto.CreateInvoiceRequest;
import com.remitlytics.core_engine.dto.InvoiceResponse;
import com.remitlytics.core_engine.dto.UpdateStatusRequest;
import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.service.InvoiceService;
import com.remitlytics.core_engine.service.PdfExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PdfExportService pdfExportService;

    @PostMapping("/invoices")
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody CreateInvoiceRequest request){
        Invoice entity = invoiceService.createDraftInvoice(
                request.clientId(),
                request.amountCents(),
                request.dueDate()
        );

        InvoiceResponse response = new InvoiceResponse(
                entity.getId(),
                entity.getClient().getId(),
                entity.getClient().getName(),
                entity.getAmountCents(),
                entity.getStatus(),
                entity.getDueDate(),
                entity.getCreatedAt(),
                entity.getPlatformFeeCents(),
                entity.getTaxCents(),
                entity.getTotalCents()
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PatchMapping("/invoices/{id}/status")
    public ResponseEntity<InvoiceResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {

        Invoice entity = invoiceService.updateInvoiceStatus(id, request.status(), request.reason());

        InvoiceResponse response = new InvoiceResponse(
                entity.getId(),
                entity.getClient().getId(),
                entity.getClient().getName(),
                entity.getAmountCents(),
                entity.getStatus(),
                entity.getDueDate(),
                entity.getCreatedAt(),
                entity.getPlatformFeeCents(),
                entity.getTaxCents(),
                entity.getTotalCents()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable UUID id) {
        InvoiceResponse invoice = invoiceService.getInvoiceById(id);
        byte[] pdfBytes = pdfExportService.generateInvoicePdf(invoice);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "invoice_" + id + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
