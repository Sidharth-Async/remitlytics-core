package com.remitlytics.core_engine.controller;

import com.remitlytics.core_engine.dto.CreateInvoiceRequest;
import com.remitlytics.core_engine.dto.InvoiceResponse;
import com.remitlytics.core_engine.dto.UpdateStatusRequest;
import com.remitlytics.core_engine.model.entities.Invoice;
import com.remitlytics.core_engine.service.InvoiceService;
import com.remitlytics.core_engine.service.PdfExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(
        origins = "http://localhost:3000",
        allowedHeaders = "*",
        exposedHeaders = {"Content-Disposition"},
        methods = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.PATCH,
                RequestMethod.DELETE,
                RequestMethod.OPTIONS
        }
)
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PdfExportService pdfExportService;

    @PostMapping("/invoices")
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
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
                entity.getPlatformFeeCents(),
                entity.getTaxCents(),
                entity.getTotalCents(),
                entity.getStatus().name(),
                entity.getDueDate().toString(),
                entity.getCreatedAt().toString()
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PatchMapping("/invoices/{id}/status")
    public ResponseEntity<InvoiceResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {

        InvoiceResponse response = invoiceService.updateInvoiceStatus(id, request.status(), request.reason());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable UUID id) {
        InvoiceResponse invoice = invoiceService.getInvoiceById(id);
        byte[] pdfBytes = pdfExportService.generateInvoicePdf(invoice);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("invoice-" + id + ".pdf")
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices(
            @RequestHeader("X-API-KEY") String apiKey) {
        List<InvoiceResponse> invoices = invoiceService.getAllInvoicesForTenant(apiKey);
        return ResponseEntity.ok(invoices);
    }
}