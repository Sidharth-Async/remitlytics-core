package com.remitlytics.core_engine.service;

import com.remitlytics.core_engine.dto.InvoiceBreakdown;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class InvoiceCalculationServiceImpl implements InvoiceCalculationService{

    /*      1. Convert baseAmountCents to BigDecimal.
            2. Platform Fee = baseAmountCents×(feePercentage/100). Round to whole number
                using .setScale(0, RoundingMode.HALF_UP).
            3. Taxable Subtotal = baseAmountCents+Platform Fee.
            4. Tax = Taxable Subtotal×(taxPercentage/100). Round to whole number using
                .setScale(0, RoundingMode.HALF_UP).
            5. Total = Taxable Subtotal+Tax.
            6. Convert all final BigDecimal results back to primitive long via
                .longValue() and return the InvoiceBreakdown record.  */

    @Override
    public InvoiceBreakdown calculate(long baseAmountCents, double feePercentage, double taxPercentage) {
        BigDecimal baseAmount = BigDecimal.valueOf(baseAmountCents);
        BigDecimal hundred = BigDecimal.valueOf(100);

        BigDecimal fee = baseAmount.multiply(BigDecimal.valueOf(feePercentage))
                .divide(hundred, 0, RoundingMode.HALF_UP);
        BigDecimal taxableSubtotal = baseAmount.add(fee);
        BigDecimal tax = taxableSubtotal.multiply(BigDecimal.valueOf(taxPercentage))
                .divide(hundred, 0, RoundingMode.HALF_UP);

        BigDecimal total = taxableSubtotal.add(tax);

        return new InvoiceBreakdown(
                baseAmountCents,
                fee.longValue(),
                tax.longValue(),
                total.longValue()
        );
    }
}
