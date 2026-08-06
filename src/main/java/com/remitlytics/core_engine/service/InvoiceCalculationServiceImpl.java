package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.InvoiceBreakdown;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class InvoiceCalculationServiceImpl implements InvoiceCalculationService {

    @Override
    public InvoiceBreakdown calculate(long baseAmountCents, double platformFeePercent, double taxPercent) {
        if (baseAmountCents < 0) {
            throw new IllegalArgumentException("Base amount cents must be a non-negative number.");
        }

        BigDecimal base = BigDecimal.valueOf(baseAmountCents);

        // 1. Calculate Platform Fee: (base * feePercent) / 100
        BigDecimal feeRate = BigDecimal.valueOf(platformFeePercent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        long platformFeeCents = base.multiply(feeRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        // 2. Taxable Subtotal = base + platformFee
        BigDecimal taxableSubtotal = base.add(BigDecimal.valueOf(platformFeeCents));

        // 3. Calculate Tax: (taxableSubtotal * taxPercent) / 100
        BigDecimal taxRate = BigDecimal.valueOf(taxPercent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        long taxCents = taxableSubtotal.multiply(taxRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        // 4. Total = base + platformFee + tax
        long totalCents = baseAmountCents + platformFeeCents + taxCents;

        return new InvoiceBreakdown(baseAmountCents, platformFeeCents, taxCents, totalCents);
    }
}