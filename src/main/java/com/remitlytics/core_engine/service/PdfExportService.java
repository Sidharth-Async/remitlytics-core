package com.remitlytics.core_engine.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.remitlytics.core_engine.dto.InvoiceResponse;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
public class PdfExportService {

    public byte[] generateInvoicePdf(InvoiceResponse invoice) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Header Banner
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(15, 23, 42));
            Paragraph title = new Paragraph("REMITLYTICS TAX INVOICE", titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            document.add(title);

            Paragraph subtitle = new Paragraph("Invoice ID: " + invoice.id() + " | Status: " + invoice.status(),
                    FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY));
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // Table Breakdown
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);

            // Convert cents to dollars safely using double conversion
            table.addCell("Base Amount:");
            table.addCell("$" + String.format("%.2f", (double) invoice.amountCents() / 100.0));

            table.addCell("Platform Fee (1.5%):");
            table.addCell("$" + String.format("%.2f", (double) invoice.platformFeeCents() / 100.0));

            table.addCell("Tax (18.0% GST):");
            table.addCell("$" + String.format("%.2f", (double) invoice.taxCents() / 100.0));

            // Dynamic label based on payment status
            String totalLabel = "PAID".equalsIgnoreCase(invoice.status().name()) ? "Total Amount Paid:" : "Total Amount Due:";
            table.addCell(totalLabel);
            table.addCell("$" + String.format("%.2f", (double) invoice.totalCents() / 100.0));

            document.add(table);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF document", e);
        }

        return out.toByteArray();
    }
}