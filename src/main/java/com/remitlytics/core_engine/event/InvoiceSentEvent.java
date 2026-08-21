package com.remitlytics.core_engine.event;

import com.remitlytics.core_engine.dto.InvoiceResponse;

public record InvoiceSentEvent(
        InvoiceResponse invoice,
        String recipientEmail,
        String recipientName
) {}