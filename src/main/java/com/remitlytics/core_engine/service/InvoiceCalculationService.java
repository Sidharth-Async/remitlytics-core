package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.InvoiceBreakdown;
import org.springframework.stereotype.Service;

@Service
public interface InvoiceCalculationService {

    InvoiceBreakdown calculate(long baseAmountCents, double feePercentage,
                               double taxPercentage);
}
